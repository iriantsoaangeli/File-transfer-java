package util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Utilitaires réseau.
 * Fournit des méthodes pour lister les interfaces réseau disponibles
 * et obtenir les adresses IP de la machine.
 */
public final class NetworkUtils {

    private NetworkUtils() { /* Utilitaire – pas d'instanciation */ }

    /**
     * Retourne la liste de toutes les adresses IPv4 disponibles sur la machine,
     * excluant la boucle locale (127.x.x.x) et les adresses de type link-local.
     *
     * @return liste d'adresses IPv4 sous forme de chaînes (ex : "192.168.1.10")
     */
    public static List<String> getLocalIPv4Addresses() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return addresses;

            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;

                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    String hostAddr = addr.getHostAddress();
                    // Filtrer IPv4 seulement (pas d'adresse IPv6 avec '%')
                    if (!addr.isLoopbackAddress()
                            && !addr.isLinkLocalAddress()
                            && !hostAddr.contains(":")) {
                        addresses.add(hostAddr);
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("[NetworkUtils] Erreur lecture interfaces : " + e.getMessage());
        }

        // Toujours ajouter localhost en dernier recours
        if (addresses.isEmpty()) {
            addresses.add("127.0.0.1");
        }
        return addresses;
    }

    /**
     * Retourne une liste de toutes les interfaces réseau avec leur nom et adresses.
     * Format : "eth0 - 192.168.1.10"
     *
     * @return liste de chaînes descriptives
     */
    public static List<String> getNetworkInterfaceDescriptions() {
        List<String> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return result;

            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!ni.isUp()) continue;

                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    String hostAddr = addr.getHostAddress();
                    if (!hostAddr.contains(":")) { // IPv4 uniquement
                        result.add(ni.getName() + " - " + hostAddr);
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("[NetworkUtils] Erreur lecture interfaces : " + e.getMessage());
        }

        if (result.isEmpty()) {
            result.add("loopback - 127.0.0.1");
        }
        return result;
    }

    /**
     * Extrait l'adresse IP d'une description retournée par getNetworkInterfaceDescriptions().
     * Format attendu : "nom - adresse"
     *
     * @param description chaîne descriptive
     * @return adresse IP extraite, ou chaîne vide si format invalide
     */
    public static String extractIP(String description) {
        if (description == null) return "";
        int idx = description.lastIndexOf(" - ");
        if (idx < 0) return description.trim();
        return description.substring(idx + 3).trim();
    }

    /**
     * Calcule les deux octets du port pour la réponse PASV.
     * port = p1 * 256 + p2
     *
     * @param port numéro de port
     * @return tableau [p1, p2]
     */
    public static int[] portToOctets(int port) {
        return new int[]{port / 256, port % 256};
    }

    /**
     * Formate l'adresse IP et le port pour la réponse PASV :
     * (h1,h2,h3,h4,p1,p2) où les points de l'IP sont remplacés par des virgules.
     *
     * @param ip   adresse IP (ex : "192.168.1.10")
     * @param port numéro de port de données
     * @return chaîne formatée pour PASV (ex : "192,168,1,10,7,208")
     */
    public static String formatPasvAddress(String ip, int port) {
        String ipCommas = ip.replace('.', ',');
        int[] octets = portToOctets(port);
        return ipCommas + "," + octets[0] + "," + octets[1];
    }
}
