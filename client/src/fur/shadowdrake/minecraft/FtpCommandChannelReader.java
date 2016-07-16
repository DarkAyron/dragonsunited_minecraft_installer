/*
 * Copyright (C) 2016 ayron
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fur.shadowdrake.minecraft;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ayron
 */
public class FtpCommandChannelReader extends Reader {

    private final StringBuffer sb;
    private final StringBuffer lb;
    private final Socket commandSocket;
    private final InputStreamReader isr;
    private final LogOutput log;
    private boolean closed;
    private final Thread thr;
    private final Object mutex;

    public FtpCommandChannelReader(Socket commandSocket, LogOutput log) throws IOException {
        super();
        this.commandSocket = commandSocket;
        mutex = new Object();
        isr = new InputStreamReader(commandSocket.getInputStream());
        sb = new StringBuffer();
        lb = new StringBuffer();
        this.log = log;
        closed = false;
        thr = new Thread(() -> {
            int n = 0;
            char[] cbuf = new char[4096];
            while (n >= 0) {
                try {
                    n = isr.read(cbuf);
                    synchronized (mutex) {
                        mutex.notify();
                        if (n > 0) {
                            sb.append(cbuf, 0, n);
                            lb.append(cbuf, 0, n);
                            
                            for (int m = 0; m < n; m++) {
                                if (cbuf[m] == '\n') {
                                    logLine();
                                }
                            }
                        }
                    }
                } catch (IOException ex) {
                    break;
                }
            }
            closed = true;
        }, "FTP Command Channel");
    }

    public void start() {
        thr.start();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        synchronized (mutex) {
            int n;
            if ((sb.length() == 0) && !closed) {
                try {
                    mutex.wait();
                } catch (InterruptedException ex) {
                    Logger.getLogger(FtpCommandChannelReader.class.getName()).log(Level.WARNING, "read", ex);
                    throw new IOException(ex);
                }
            } else if ((sb.length() == 0 && closed)) {
                throw new IOException("Closed");
            }

            n = (sb.length() > len) ? len : sb.length();
            for (int m = 0; m < n; m++) {
                cbuf[m + off] = sb.charAt(m);
            }

            /* consume chars */
            sb.delete(0, n);
            return n;
        }
    }

    @Override
    public void close() throws IOException {
        isr.close();
    }

    private void logLine() {
        int n = lb.indexOf("\n");
        String line = lb.substring(0, n).trim();

        log.println(line);
        /* consume the line */
        lb.delete(0, n + 1);
    }
}
