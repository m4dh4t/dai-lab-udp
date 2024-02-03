package ch.heig.dai.lab.orchestra.auditor;

import com.google.gson.Gson;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static java.nio.charset.StandardCharsets.*;

public class Auditor {

    private static final int UDP_PORT = 9904;
    private static final String MULTICAST_ADDRESS = "239.255.22.5";
    private static final int TCP_PORT = 2205;
    private static final Map<String, String> instruments = Map.of(
            "ti-ta-ti", "piano",
            "pouet", "trumpet",
            "trulu", "flute",
            "gzi-gzi", "violin",
            "boum-boum", "drum"
    );
    private static final Map<String, MusicianInfo> activeMusicians = new HashMap<>();

    private abstract static class MusicianMeta {
        protected final String uuid;
        protected final long timestamp;
        public MusicianMeta(String uuid, long timestamp) {
            this.uuid = uuid;
            this.timestamp = timestamp;
        }
    }

    private static class MusicianPayload extends MusicianMeta {
        private final String sound;
        public MusicianPayload(String uuid, String sound, long timestamp) {
            super(uuid, timestamp);
            this.sound = sound;
        }
    }

    private static class MusicianInfo extends MusicianMeta {
        private final String instrument;
        public MusicianInfo(MusicianPayload payload) {
            super(payload.uuid, payload.timestamp);
            this.instrument = instruments.get(payload.sound);
        }
    }

    public static void main(String[] args) {
        try (MulticastSocket multicastSocket = new MulticastSocket(UDP_PORT);
             ServerSocket serverSocket = new ServerSocket(TCP_PORT);
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Start TCP client handler
            Runnable tcpClientHandler = new TCPClientHandler(serverSocket);
            executor.execute(tcpClientHandler);

            // Start UDP client handler
            Runnable udpClientHandler = new UDPClientHandler(multicastSocket);
            executor.execute(udpClientHandler);
        } catch (IOException e) {
            System.out.println("Error opening server socket: " + e.getMessage());
        }
    }

    private record UDPClientHandler(MulticastSocket socket) implements Runnable {
        @Override
        public void run() {
            try {
                // Join multicast group
                InetSocketAddress group_address =  new InetSocketAddress(MULTICAST_ADDRESS, UDP_PORT);
                NetworkInterface netif = NetworkInterface.getByName("eth0");
                socket.joinGroup(group_address, netif);

                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (true) {
                    // Receive UDP packet
                    socket.receive(packet);
                    String message = new String(packet.getData(), packet.getOffset(), packet.getLength(), UTF_8);

                    // Parse JSON payload
                    MusicianPayload payload = new Gson().fromJson(message, MusicianPayload.class);
                    MusicianInfo musicianInfo = new MusicianInfo(payload);

                    // Add musician to active musicians
                    activeMusicians.put(musicianInfo.uuid, musicianInfo);
                }
            } catch (IOException e) {
                System.err.println("Error receiving UDP packet: " + e.getMessage());
            }
        }
    }

    private record TCPClientHandler(ServerSocket serverSocket) implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    Socket socket = serverSocket.accept();

                    // Remove inactive musicians
                    activeMusicians.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().timestamp > 5000);

                    // Send JSON payload to client
                    OutputStream outputStream = socket.getOutputStream();

                    // Convert activeMusicians map to JSON
                    String jsonPayload = new Gson().toJson(activeMusicians.values());

                    // Send JSON payload to the connected client
                    outputStream.write(jsonPayload.getBytes());
                }
            } catch (IOException e) {
                System.err.println("Could not send JSON payload to client");
            }
        }
    }
}