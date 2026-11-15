package pro.sketchware.compiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The networking class emitted into every project built with Sketchware used to
 * install an X509TrustManager that accepted any certificate and a HostnameVerifier
 * that returned true, which disabled TLS validation in the user's shipped app.
 * These tests pin the generated source so that never comes back.
 */
public class RequestNetworkControllerSourceTest {

    private static String source() {
        return ComponentSourceGenerator.g("com.example.app");
    }

    @Test
    public void generatedControllerNeverDisablesCertificateValidation() {
        String src = source();
        for (String marker : new String[]{
                "checkServerTrusted",
                "checkClientTrusted",
                "X509TrustManager",
                "trustAllCerts",
                "HostnameVerifier",
                "hostnameVerifier",
                "sslSocketFactory",
                "SSLContext"}) {
            assertFalse("generated source still contains " + marker, src.contains(marker));
        }
    }

    @Test
    public void generatedControllerKeepsItsTimeouts() {
        String src = source();
        assertTrue(src.contains("builder.connectTimeout(SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);"));
        assertTrue(src.contains("builder.readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS);"));
        assertTrue(src.contains("builder.writeTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS);"));
        assertTrue(src.contains("import java.util.concurrent.TimeUnit;"));
    }

    @Test
    public void generatedControllerDeclaresTheRequestedPackage() {
        assertTrue(source().startsWith("package com.example.app;"));
    }

    @Test
    public void generatedControllerImportsNothingItDoesNotUse() {
        String src = source();
        String afterImports = src.substring(src.indexOf("public class RequestNetworkController"));
        for (String line : src.split("\r\n")) {
            if (!line.startsWith("import ")) continue;
            String fqn = line.substring("import ".length(), line.length() - 1);
            String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
            assertTrue("unused import: " + fqn, afterImports.contains(simple));
        }
    }

    @Test
    public void generatedControllerIsBalanced() {
        String src = source();
        assertEquals("unbalanced braces", count(src, '{'), count(src, '}'));
        assertEquals("unbalanced parens", count(src, '('), count(src, ')'));
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }
}
