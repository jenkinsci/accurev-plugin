// ========================================================================
// Copyright 1998-2005 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package hudson.plugins.jetty.security;

/**
 * Password utility class.
 * <p/>
 * Passwords that begin with OBF: are de obfuscated.
 * Passwords can be obfuscated by run org.mortbay.util.Password as a
 * main class.  Obfuscated password are required if a system needs
 * to recover the full password (eg. so that it may be passed to another
 * system). They are not secure, but prevent casual observation.
 *
 * @author Greg Wilkins (gregw)
 */
public final class Password {

    /** Do not instantiate Password. */
    private Password() {
    }

    public static String obfuscate(String s) {
        StringBuffer buf = new StringBuffer();
        byte[] b = s.getBytes();
        synchronized (buf) {
            buf.append(__OBFUSCATE);
            for (int i = 0; i < b.length; i++) {
                byte b1 = b[i];
                byte b2 = b[s.length() - (i + 1)];
                int i1 = 127 + b1 + b2;
                int i2 = 127 + b1 - b2;
                int i0 = i1 * 256 + i2;
                String x = Integer.toString(i0, 36);
                switch (x.length()) {
                    case 1:
                        buf.append('0');
                    case 2:
                        buf.append('0');
                    case 3:
                        buf.append('0');
                    default:
                        buf.append(x);
                }
            }
            return buf.toString();
        }
    }

    public static final String __OBFUSCATE = "OBF:";

    public static String deobfuscate(String s) {
        if (s.startsWith(__OBFUSCATE))
            s = s.substring(4);
        byte[] b = new byte[s.length() / 2];
        int l = 0;
        for (int i = 0; i < s.length(); i += 4) {
            String x = s.substring(i, i + 4);
            int i0 = Integer.parseInt(x, 36);
            int i1 = (i0 / 256);
            int i2 = (i0 % 256);
            b[l++] = (byte) ((i1 + i2 - 254) / 2);
        }
        return new String(b, 0, l);
    }
}
