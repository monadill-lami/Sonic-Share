package com.sonicshare.android;

import org.junit.Test;

import static org.junit.Assert.*;

public class MainActivityTest {

    @Test
    public void testIpValidation() {
        assertTrue(MainActivity.isValidIp("192.168.1.1"));
        assertTrue(MainActivity.isValidIp("10.0.0.1"));
        assertTrue(MainActivity.isValidIp("127.0.0.1"));
        assertTrue(MainActivity.isValidIp("255.255.255.255"));

        assertFalse(MainActivity.isValidIp(""));
        assertFalse(MainActivity.isValidIp(null));
        assertFalse(MainActivity.isValidIp("192.168.1."));
        assertFalse(MainActivity.isValidIp("192.168.1"));
        assertFalse(MainActivity.isValidIp("192.168.1.1.1"));
        assertFalse(MainActivity.isValidIp("abc.def.ghi.jkl"));
        assertFalse(MainActivity.isValidIp("192.168.1.256"));
    }

    @Test
    public void testPortValidation() {
        assertTrue(MainActivity.isValidPort("50005"));
        assertTrue(MainActivity.isValidPort("1"));
        assertTrue(MainActivity.isValidPort("65535"));
        assertTrue(MainActivity.isValidPort("8080"));

        assertFalse(MainActivity.isValidPort(""));
        assertFalse(MainActivity.isValidPort(null));
        assertFalse(MainActivity.isValidPort("0"));
        assertFalse(MainActivity.isValidPort("-1"));
        assertFalse(MainActivity.isValidPort("65536"));
        assertFalse(MainActivity.isValidPort("port"));
        assertFalse(MainActivity.isValidPort("12.34"));
    }

    @Test
    public void testSharedPreferencesConstants() {
        assertEquals("SonicSharePrefs", MainActivity.PREFS_NAME);
        assertEquals("last_ip", MainActivity.KEY_LAST_IP);
        assertEquals("last_port", MainActivity.KEY_LAST_PORT);
    }
}
