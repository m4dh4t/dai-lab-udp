package ch.heig.dai.lab.orchestra.musician;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.*;

public class Musician {
    private static final String MULTICAST_ADDRESS = "239.255.22.5";
    private static final int MULTICAST_PORT = 9904;
    private static final Map<String, String> instruments = Map.of(
        "piano", "ti-ta-ti",
        "trumpet", "pouet",
        "flute", "trulu",
        "violin", "gzi-gzi",
        "drum", "boum-boum"
    );

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -jar musician.jar <instrument>");
            return;
        }

        String instrument = args[0];
        if (!instruments.containsKey(instrument)) {
            System.err.println("Invalid instrument, valid instruments are: " + instruments.keySet());
            return;
        }

        String musicianUUID = UUID.randomUUID().toString();
        try (DatagramSocket socket = new DatagramSocket()) {
            while (true) {
                byte[] payload = new Gson().toJson(Map.of(
                        "uuid", musicianUUID,
                        "sound", instruments.get(instrument),
                        "timestamp", System.currentTimeMillis()
                )).getBytes(UTF_8);

                InetSocketAddress dest_address = new InetSocketAddress(MULTICAST_ADDRESS, MULTICAST_PORT);
                DatagramPacket packet = new DatagramPacket(payload, payload.length, dest_address);

                socket.send(packet);

                System.out.println("Sent packet: " + new String(payload, UTF_8));

                Thread.sleep(1000);
            }
        } catch (IOException | InterruptedException ex) {
            System.err.println(ex.getMessage());
        }
    }
}