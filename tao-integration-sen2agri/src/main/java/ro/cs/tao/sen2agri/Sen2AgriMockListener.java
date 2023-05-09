package ro.cs.tao.sen2agri;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class Sen2AgriMockListener {
    private final ServerSocket socket;
    private final Thread thread;
    private volatile boolean stopped;

    public Sen2AgriMockListener(int port) throws IOException {
        this.socket = new ServerSocket(port);
        this.thread = new Thread(() -> {
            try {
                while (!stopped) {
                    onConnection(this.socket.accept());
                }
            } catch (Exception e) {
                System.out.println(String.format("Listener on port %d closed", port));
            }
        });
    }

    public void start() {
        this.thread.start();
    }

    public void shutdown() {
        stopped = true;
        try {
            this.socket.close();
        } catch (IOException ignored) {
        }
    }

    private void onConnection(Socket socket) {
        final StringBuilder builder = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                builder.append(line).append(" ");
            }
            System.out.println(getClass().getSimpleName() + " received: " + builder.toString());
            builder.setLength(0);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

}
