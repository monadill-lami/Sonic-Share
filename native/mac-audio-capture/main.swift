import Foundation
import ScreenCaptureKit
import AVFoundation
import CoreMedia
import CoreGraphics

// Step 1: Check Screen Recording permission
if !CGPreflightScreenCaptureAccess() {
    _ = CGRequestScreenCaptureAccess()
    if let data = "ScreenCaptureAccessDenied\n".data(using: .utf8) {
        FileHandle.standardError.write(data)
    }
    exit(2)
}

// Ignore SIGPIPE so writing to closed stdout does not crash with unhandled signal
signal(SIGPIPE, SIG_IGN)
signal(SIGINT, SIG_IGN)
signal(SIGTERM, SIG_IGN)

final class AudioCaptureDelegate: NSObject, SCStreamOutput {
    private var cachedConverter: AVAudioConverter?
    private var cachedInputFormat: AVAudioFormat?
    private let targetFormat: AVAudioFormat

    override init() {
        guard let format = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 44100, channels: 1, interleaved: true) else {
            fatalError("Failed to create target audio format")
        }
        self.targetFormat = format
        super.init()
    }

    func stream(_ stream: SCStream, didOutputSampleBuffer sampleBuffer: CMSampleBuffer, of type: SCStreamOutputType) {
        guard type == .audio else { return }
        guard let formatDescription = sampleBuffer.formatDescription else { return }

        let inFormat = AVAudioFormat(cmAudioFormatDescription: formatDescription)

        if cachedConverter == nil || cachedInputFormat != inFormat {
            cachedConverter = AVAudioConverter(from: inFormat, to: targetFormat)
            cachedInputFormat = inFormat
        }

        guard let converter = cachedConverter else { return }

        let numSamples = sampleBuffer.numSamples
        guard numSamples > 0 else { return }

        do {
            try sampleBuffer.withAudioBufferList { audioBufferList, _ in
                guard let inBuffer = AVAudioPCMBuffer(pcmFormat: inFormat, bufferListNoCopy: audioBufferList.unsafePointer) else {
                    return
                }
                inBuffer.frameLength = AVAudioFrameCount(numSamples)

                let sampleRateRatio = targetFormat.sampleRate / inFormat.sampleRate
                let estimatedOutFrames = AVAudioFrameCount(ceil(Double(inBuffer.frameLength) * sampleRateRatio)) + 64
                guard let outBuffer = AVAudioPCMBuffer(pcmFormat: targetFormat, frameCapacity: estimatedOutFrames) else {
                    return
                }

                var error: NSError?
                var hasSuppliedData = false
                let inputBlock: AVAudioConverterInputBlock = { _, outStatus in
                    if !hasSuppliedData {
                        hasSuppliedData = true
                        outStatus.pointee = .haveData
                        return inBuffer
                    } else {
                        outStatus.pointee = .noDataNow
                        return nil
                    }
                }

                let status = converter.convert(to: outBuffer, error: &error, withInputFrom: inputBlock)
                if (status == .haveData || status == .inputRanDry), outBuffer.frameLength > 0, let channelData = outBuffer.int16ChannelData {
                    let byteCount = Int(outBuffer.frameLength) * 2 // 1 channel * 2 bytes per 16-bit sample
                    let data = Data(bytes: channelData[0], count: byteCount)
                    do {
                        try FileHandle.standardOutput.write(contentsOf: data)
                    } catch {
                        // Stdout closed (e.g. parent process terminated)
                        exit(0)
                    }
                }
            }
        } catch {
            // Buffer list access error
        }
    }
}

final class StreamDelegate: NSObject, SCStreamDelegate {
    func stream(_ stream: SCStream, didStopWithError error: Error) {
        if let errData = "ScreenCaptureKit stream stopped with error: \(error.localizedDescription)\n".data(using: .utf8) {
            FileHandle.standardError.write(errData)
        }
        exit(1)
    }
}

var activeStream: SCStream?
var activeDelegate: AudioCaptureDelegate?
var activeStreamDelegate: StreamDelegate?
var signalSources: [DispatchSourceSignal] = []

func stopAndExit() {
    if let stream = activeStream {
        activeStream = nil
        stream.stopCapture { _ in
            exit(0)
        }
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.5) {
            exit(0)
        }
    } else {
        exit(0)
    }
}

let sigintSource = DispatchSource.makeSignalSource(signal: SIGINT, queue: .main)
sigintSource.setEventHandler {
    stopAndExit()
}
sigintSource.resume()
signalSources.append(sigintSource)

let sigtermSource = DispatchSource.makeSignalSource(signal: SIGTERM, queue: .main)
sigtermSource.setEventHandler {
    stopAndExit()
}
sigtermSource.resume()
signalSources.append(sigtermSource)

SCShareableContent.getExcludingDesktopWindows(false, onScreenWindowsOnly: true) { content, error in
    if let error = error {
        if let errData = "Failed to get shareable content: \(error.localizedDescription)\n".data(using: .utf8) {
            FileHandle.standardError.write(errData)
        }
        exit(1)
    }

    guard let display = content?.displays.first else {
        if let errData = "No display found for audio capture\n".data(using: .utf8) {
            FileHandle.standardError.write(errData)
        }
        exit(1)
    }

    let filter = SCContentFilter(display: display, excludingApplications: [], exceptingWindows: [])
    let config = SCStreamConfiguration()
    config.capturesAudio = true
    config.excludesCurrentProcessAudio = true
    config.queueDepth = 5
    config.sampleRate = 44100
    config.channelCount = 1

    let streamDelegate = StreamDelegate()
    activeStreamDelegate = streamDelegate

    let stream = SCStream(filter: filter, configuration: config, delegate: streamDelegate)

    let audioDelegate = AudioCaptureDelegate()
    activeDelegate = audioDelegate

    let audioQueue = DispatchQueue(label: "com.sonicshare.mac-audio-capture", qos: .userInteractive)

    do {
        try stream.addStreamOutput(audioDelegate, type: .audio, sampleHandlerQueue: audioQueue)
    } catch {
        if let errData = "Failed to add stream output: \(error.localizedDescription)\n".data(using: .utf8) {
            FileHandle.standardError.write(errData)
        }
        exit(1)
    }

    activeStream = stream
    stream.startCapture { error in
        if let error = error {
            if let errData = "Failed to start audio stream capture: \(error.localizedDescription)\n".data(using: .utf8) {
                FileHandle.standardError.write(errData)
            }
            exit(1)
        }
    }
}

RunLoop.main.run()
