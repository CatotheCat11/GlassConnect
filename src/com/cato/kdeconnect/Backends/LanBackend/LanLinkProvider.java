/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package com.cato.kdeconnect.Backends.LanBackend;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Network;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.conscrypt.Conscrypt;
import org.json.JSONException;
import com.cato.kdeconnect.Backends.BaseLink;
import com.cato.kdeconnect.Backends.BaseLinkProvider;
import com.cato.kdeconnect.DeviceHost;
import com.cato.kdeconnect.DeviceInfo;
import com.cato.kdeconnect.Helpers.DeviceHelper;
import com.cato.kdeconnect.Helpers.SecurityHelpers.SslHelper;
import com.cato.kdeconnect.Helpers.ThreadHelper;
import com.cato.kdeconnect.Helpers.TrustedNetworkHelper;
import com.cato.kdeconnect.NetworkPacket;
import com.cato.kdeconnect.UserInterface.CustomDevicesActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import kotlin.text.Charsets;

/**
 * This LanLinkProvider creates {@link LanLink}s to other devices on the same
 * WiFi network. The first packet sent over a socket must be an
 * {@link DeviceInfo#toIdentityPacket()}.
 *
 * @see #identityPacketReceived(NetworkPacket, Socket, LanLink.ConnectionStarted, boolean)
 */
public class LanLinkProvider extends BaseLinkProvider {

    final static int UDP_PORT = 1716;
    final static int MIN_PORT = 1716;
    final static int MAX_PORT = 1764;
    final static int PAYLOAD_TRANSFER_MIN_PORT = 1739;

    final static int MAX_IDENTITY_PACKET_SIZE = 1024 * 512;
    final static int MAX_UDP_PACKET_SIZE = 1024 * 512;

    final static long MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE = 500L;

    private final Context context;

    final HashMap<String, LanLink> visibleDevices = new HashMap<>(); // Links by device id

    final ConcurrentHashMap<String, Long> lastConnectionTime = new ConcurrentHashMap<>();

    private ServerSocket tcpServer;
    private DatagramSocket udpServer;

    private final MdnsDiscovery mdnsDiscovery;

    private long lastBroadcast = 0;
    private final static long delayBetweenBroadcasts = 200;

    private boolean listening = false;

    public void onConnectionLost(BaseLink link) {
        String deviceId = link.getDeviceId();
        visibleDevices.remove(deviceId);
        super.onConnectionLost(link);
    }

    //They received my UDP broadcast and are connecting to me. The first thing they send should be their identity packet.
    @WorkerThread
    private void tcpPacketReceived(Socket socket) throws IOException {

        NetworkPacket networkPacket;
        try {
            String message = readSingleLine(socket);
            networkPacket = NetworkPacket.unserialize(message);
            //Log.e("TcpListener", "Received TCP packet: " + networkPacket.serialize());
        } catch (Exception e) {
            Log.e("KDE/LanLinkProvider", "Exception while receiving TCP packet", e);
            return;
        }

        Log.i("KDE/LanLinkProvider", "identity packet received from a TCP connection from " + networkPacket.getString("deviceName"));

        boolean deviceTrusted = isDeviceTrusted(networkPacket.getString("deviceId"));
        if (!deviceTrusted && !TrustedNetworkHelper.isTrustedNetwork(context)) {
            Log.i("KDE/LanLinkProvider", "Ignoring identity packet because the device is not trusted and I'm not on a trusted network.");
            return;
        }

        identityPacketReceived(networkPacket, socket, LanLink.ConnectionStarted.Locally, deviceTrusted);
    }

    /**
     * Read a single line from a socket without consuming anything else from the input.
     */
    private String readSingleLine(Socket socket) throws IOException {
        InputStream stream = socket.getInputStream();
        StringBuilder line = new StringBuilder(MAX_IDENTITY_PACKET_SIZE);
        int ch;
        while ((ch = stream.read()) != -1) {
            line.append((char) ch);
            if (ch == '\n') {
                return line.toString();
            }
            if (line.length() >= MAX_IDENTITY_PACKET_SIZE) {
                break;
            }
        }
        throw new IOException("Couldn't read a line from the socket");
    }

    //I've received their broadcast and should connect to their TCP socket and send my identity.
    @WorkerThread
    private void udpPacketReceived(DatagramPacket packet) throws JSONException, IOException {

        final InetAddress address = packet.getAddress();

        String message = new String(packet.getData(), Charsets.UTF_8);
        final NetworkPacket identityPacket;
        try {
            identityPacket = NetworkPacket.unserialize(message);
        } catch (JSONException e) {
            Log.w("KDE/LanLinkProvider", "Invalid identity packet received: " + e.getMessage());
            return;
        }

        if (!DeviceInfo.isValidIdentityPacket(identityPacket)) {
            Log.w("KDE/LanLinkProvider", "Invalid identity packet received.");
            return;
        }

        final String deviceId = identityPacket.getString("deviceId");
        String myId = DeviceHelper.getDeviceId(context);
        if (deviceId.equals(myId)) {
            //Ignore my own broadcast
            return;
        }

        long now = System.currentTimeMillis();
        Long last =  lastConnectionTime.get(deviceId);
        if (last != null && (last + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE > now)) {
            Log.i("LanLinkProvider", "Discarding second UDP packet from the same device " + deviceId + " received too quickly");
            return;
        }
        lastConnectionTime.put(deviceId, now);

        int tcpPort = identityPacket.getInt("tcpPort", MIN_PORT);
        if (tcpPort < MIN_PORT || tcpPort > MAX_PORT) {
            Log.e("LanLinkProvider", "TCP port outside of kdeconnect's range");
            return;
        }

        Log.i("KDE/LanLinkProvider", "Broadcast identity packet received from " + identityPacket.getString("deviceName"));

        boolean deviceTrusted = isDeviceTrusted(identityPacket.getString("deviceId"));
        if (!deviceTrusted && !TrustedNetworkHelper.isTrustedNetwork(context)) {
            Log.i("KDE/LanLinkProvider", "Ignoring identity packet because the device is not trusted and I'm not on a trusted network.");
            return;
        }

        SocketFactory socketFactory = SocketFactory.getDefault();
        Socket socket = socketFactory.createSocket(address, tcpPort);
        configureSocket(socket);

        DeviceInfo myDeviceInfo = DeviceHelper.getDeviceInfo(context);
        NetworkPacket myIdentity = myDeviceInfo.toIdentityPacket();

        OutputStream out = socket.getOutputStream();
        out.write(myIdentity.serialize().getBytes());
        out.flush();

        identityPacketReceived(identityPacket, socket, LanLink.ConnectionStarted.Remotely, deviceTrusted);
    }

    private void configureSocket(Socket socket) {
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
            socket.setKeepAlive(true);
            if (socket instanceof SSLSocket) {
                SSLSocket sslSocket = (SSLSocket) socket;

                // Create a custom SSL context that enables multiple TLS protocols
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init((KeyStore) null);
                TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

                // Try to enable TLS protocols manually
                SSLContext sslContext = SSLContext.getInstance("TLS", "Conscrypt");
                sslContext.init(null, trustManagers, new SecureRandom());

                sslSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
                sslSocket.setSSLParameters(sslContext.getDefaultSSLParameters());
            }
        } catch (Exception e) {
            Log.e("LanLink", "Exception", e);
        }
    }

    private boolean isDeviceTrusted(String deviceId) {
        SharedPreferences preferences = context.getSharedPreferences("trusted_devices", Context.MODE_PRIVATE);
        return preferences.getBoolean(deviceId, false);
    }

    /**
     * Called when a new 'identity' packet is received. Those are passed here by
     * {@link #tcpPacketReceived(Socket)} and {@link #udpPacketReceived(DatagramPacket)}.
     * Should be called on a new thread since it blocks until the handshake is completed.
     *
     * @param identityPacket    identity of a remote device
     * @param socket            a new Socket, which should be used to receive packets from the remote device
     * @param connectionStarted which side started this connection
     * @param deviceTrusted     whether the packet comes from a trusted device
     */
    @WorkerThread
    private void identityPacketReceived(final NetworkPacket identityPacket, final Socket socket, final LanLink.ConnectionStarted connectionStarted, final boolean deviceTrusted) throws IOException {

        if (!DeviceInfo.isValidIdentityPacket(identityPacket)) {
            Log.w("KDE/LanLinkProvider", "Invalid identity packet received.");
            return;
        }

        String myId = DeviceHelper.getDeviceId(context);
        final String deviceId = identityPacket.getString("deviceId");
        if (deviceId.equals(myId)) {
            Log.e("KDE/LanLinkProvider", "Somehow I'm connected to myself, ignoring. This should not happen.");
            return;
        }

        int protocolVersion = identityPacket.getInt("protocolVersion");
        if (deviceTrusted && isProtocolDowngrade(deviceId, protocolVersion)) {
            Log.w("KDE/LanLinkProvider", "Refusing to connect to a device using an older protocol version:" + protocolVersion);
            return;
        }

        if (deviceTrusted && !SslHelper.isCertificateStored(context, deviceId)) {
            Log.e("KDE/LanLinkProvider", "Device trusted but no cert stored. This should not happen.");
            return;
        }

        String deviceName = identityPacket.getString("deviceName", "unknown");
        Log.i("KDE/LanLinkProvider", "Starting SSL handshake with " + deviceName + " trusted:" + deviceTrusted);

        // If I'm the TCP server I will be the SSL client and viceversa.
        final boolean clientMode = (connectionStarted == LanLink.ConnectionStarted.Locally);
        final SSLSocket sslSocket = SslHelper.convertToSslSocket(context, socket, deviceId, deviceTrusted, clientMode);
        sslSocket.addHandshakeCompletedListener(event -> {
            // Start a new thread because some Android versions don't allow calling sslSocket.getOutputStream() from the callback
            ThreadHelper.execute(() -> {
                String mode = clientMode ? "client" : "server";
                try {
                    NetworkPacket secureIdentityPacket;
                    if (protocolVersion >= 8) {
                        DeviceInfo myDeviceInfo = DeviceHelper.getDeviceInfo(context);
                        NetworkPacket myIdentity = myDeviceInfo.toIdentityPacket();
                        OutputStream writer = sslSocket.getOutputStream();
                        writer.write(myIdentity.serialize().getBytes(Charsets.UTF_8));
                        writer.flush();
                        String line = readSingleLine(sslSocket);
                        // Do not trust the identity packet we received unencrypted
                        secureIdentityPacket = NetworkPacket.unserialize(line);
                        if (!DeviceInfo.isValidIdentityPacket(secureIdentityPacket)) {
                            throw new JSONException("Invalid identity packet");
                        }
                        int newProtocolVersion = secureIdentityPacket.getInt("protocolVersion");
                        if (newProtocolVersion != protocolVersion) {
                            Log.w("KDE/LanLinkProvider", "Protocol version changed half-way through the handshake: " + protocolVersion + " ->" + newProtocolVersion);
                        }
                    } else {
                        secureIdentityPacket = identityPacket;
                    }
                    Certificate certificate = event.getPeerCertificates()[0];
                    DeviceInfo deviceInfo = DeviceInfo.fromIdentityPacketAndCert(secureIdentityPacket, certificate);
                    Log.i("KDE/LanLinkProvider", "Handshake as " + mode + " successful with " + deviceName + " secured with " + event.getCipherSuite());
                    addOrUpdateLink(sslSocket, deviceInfo);
                } catch (JSONException e) {
                    Log.e("KDE/LanLinkProvider", "Remote device doesn't correctly implement protocol version 8", e);
                } catch (IOException e) {
                    Log.e("KDE/LanLinkProvider", "Handshake as " + mode + " failed with " + deviceName, e);
                }
            });
        });

        //Handshake is blocking, so do it on another thread and free this thread to keep receiving new connection
        Log.d("LanLinkProvider", "Starting handshake");
        sslSocket.startHandshake();
        Log.d("LanLinkProvider", "Handshake done");
    }

    private boolean isProtocolDowngrade(String deviceId, int protocolVersion) {
        SharedPreferences devicePrefs = context.getSharedPreferences(deviceId, Context.MODE_PRIVATE);
        int lastKnownProtocolVersion = devicePrefs.getInt("protocolVersion", 0);
        return lastKnownProtocolVersion > protocolVersion;
    }

    /**
     * Add or update a link in the {@link #visibleDevices} map.
     *
     * @param socket           a new Socket, which should be used to send and receive packets from the remote device
     * @param deviceInfo       remote device info
     * @throws IOException if an exception is thrown by {@link LanLink#reset(SSLSocket, DeviceInfo)}
     */
    private void addOrUpdateLink(SSLSocket socket, DeviceInfo deviceInfo) throws IOException {
        LanLink link = visibleDevices.get(deviceInfo.id);
        if (link != null) {
            if (!link.getDeviceInfo().certificate.equals(deviceInfo.certificate)) {
                Log.e("LanLinkProvider", "LanLink was asked to replace a socket but the certificate doesn't match, aborting");
                return;
            }
            // Update existing link
            Log.d("KDE/LanLinkProvider", "Reusing same link for device " + deviceInfo.id);
            link.reset(socket, deviceInfo);
            onDeviceInfoUpdated(deviceInfo);
        } else {
            // Create a new link
            Log.d("KDE/LanLinkProvider", "Creating a new link for device " + deviceInfo.id);
            link = new LanLink(context, deviceInfo, this, socket);
            visibleDevices.put(deviceInfo.id, link);
            onConnectionReceived(link);
        }
    }

    public LanLinkProvider(Context context) {
        this.context = context;
        this.mdnsDiscovery = new MdnsDiscovery(context, this);
        Security.insertProviderAt(Conscrypt.newProvider(), 1);
    }

    private void setupUdpListener() {
        try {
            udpServer = new DatagramSocket(null);
            udpServer.setReuseAddress(true);
            udpServer.setBroadcast(true);
        } catch (SocketException e) {
            Log.e("LanLinkProvider", "Error creating udp server", e);
            throw new RuntimeException(e);
        }
        try {
            udpServer.bind(new InetSocketAddress(UDP_PORT));
        } catch (SocketException e) {
            // We ignore this exception and continue without being able to receive broadcasts instead of crashing the app.
            Log.e("LanLinkProvider", "Error binding udp server. We can send udp broadcasts but not receive them", e);
        }
        ThreadHelper.execute(() -> {
            Log.i("UdpListener", "Starting UDP listener");
            while (listening) {
                try {
                    DatagramPacket packet = new DatagramPacket(new byte[MAX_UDP_PACKET_SIZE], MAX_UDP_PACKET_SIZE);
                    udpServer.receive(packet);
                    ThreadHelper.execute(() -> {
                        try {
                            udpPacketReceived(packet);
                        } catch (JSONException | IOException e) {
                            Log.e("LanLinkProvider", "Exception receiving incoming UDP connection", e);
                        }
                    });
                } catch (IOException e) {
                    Log.e("LanLinkProvider", "UdpReceive exception", e);
                    onNetworkChange(null); // Trigger a UDP broadcast to try to get them to connect to us instead
                }
            }
            Log.w("UdpListener", "Stopping UDP listener");
        });
    }

    private void setupTcpListener() {
        try {
            tcpServer = openServerSocketOnFreePort(MIN_PORT);
        } catch (IOException e) {
            Log.e("LanLinkProvider", "Error creating tcp server", e);
            throw new RuntimeException(e);
        }
        ThreadHelper.execute(() -> {
            while (listening) {
                try {
                    Socket socket = tcpServer.accept();
                    configureSocket(socket);
                    ThreadHelper.execute(() -> {
                        try {
                            tcpPacketReceived(socket);
                        } catch (IOException e) {
                            Log.e("LanLinkProvider", "Exception receiving incoming TCP connection", e);
                        }
                    });
                } catch (Exception e) {
                    Log.e("LanLinkProvider", "TcpReceive exception", e);
                }
            }
            Log.w("TcpListener", "Stopping TCP listener");
        });

    }

    static ServerSocket openServerSocketOnFreePort(int minPort) throws IOException {
        int tcpPort = minPort;
        while (tcpPort <= MAX_PORT) {
            try {
                ServerSocket candidateServer = new ServerSocket();
                candidateServer.bind(new InetSocketAddress(tcpPort));
                Log.i("KDE/LanLink", "Using port " + tcpPort);
                return candidateServer;
            } catch (IOException e) {
                tcpPort++;
                if (tcpPort == MAX_PORT) {
                    Log.e("KDE/LanLink", "No ports available");
                    throw e; //Propagate exception
                }
            }
        }
        throw new RuntimeException("This should not be reachable");
    }

    private void broadcastUdpIdentityPacket(@Nullable Network network) {

        ThreadHelper.execute(() -> {
            List<DeviceHost> hostList = CustomDevicesActivity
                    .getCustomDeviceList(context);

            if (TrustedNetworkHelper.isTrustedNetwork(context)) {
                hostList.add(DeviceHost.BROADCAST); //Default: broadcast.
            } else {
                Log.i("LanLinkProvider", "Current network isn't trusted, not broadcasting");
            }

            ArrayList<InetAddress> ipList = new ArrayList<>();
            for (DeviceHost host : hostList) {
                try {
                    ipList.add(InetAddress.getByName(host.toString()));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }

            if (ipList.isEmpty()) {
                return;
            }

            sendUdpIdentityPacket(ipList, network);
        });
    }

    @WorkerThread
    public void sendUdpIdentityPacket(List<InetAddress> ipList, @Nullable Network network) {
        if (tcpServer == null || !tcpServer.isBound()) {
            Log.i("LanLinkProvider", "Won't broadcast UDP packet if TCP socket is not ready yet");
            return;
        }

        // TODO: In protocol version 8 this packet doesn't need to contain identity info
        //       since it will be exchanged after the socket is encrypted.
        DeviceInfo myDeviceInfo = DeviceHelper.getDeviceInfo(context);
        NetworkPacket identity = myDeviceInfo.toIdentityPacket();
        identity.set("tcpPort", tcpServer.getLocalPort());

        byte[] bytes;
        try {
            bytes = identity.serialize().getBytes(Charsets.UTF_8);
        } catch (JSONException e) {
            Log.e("KDE/LanLinkProvider", "Failed to serialize identity packet", e);
            return;
        }

        DatagramSocket socket;
        try {
            socket = new DatagramSocket();
            if (network != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                try {
                    network.bindSocket(socket);
                } catch (IOException e) {
                    Log.w("LanLinkProvider", "Couldn't bind socket to the network");
                    e.printStackTrace();
                }
            }
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
        } catch (SocketException e) {
            Log.e("KDE/LanLinkProvider", "Failed to create DatagramSocket", e);
            return;
        }

        for (InetAddress ip : ipList) {
            try {
                socket.send(new DatagramPacket(bytes, bytes.length, ip, MIN_PORT));
                //Log.i("KDE/LanLinkProvider","Udp identity packet sent to address "+client);
            } catch (IOException e) {
                Log.e("KDE/LanLinkProvider", "Sending udp identity packet failed. Invalid address? (" + ip.toString() + ")", e);
            }
        }

        socket.close();
    }

    @Override
    public void onStart() {
        //Log.i("KDE/LanLinkProvider", "onStart");
        if (!listening) {

            listening = true;

            setupUdpListener();
            setupTcpListener();

            mdnsDiscovery.startDiscovering();
            if (TrustedNetworkHelper.isTrustedNetwork(context)) {
                mdnsDiscovery.startAnnouncing();
            }

            broadcastUdpIdentityPacket(null);
        }
    }

    @Override
    public void onNetworkChange(@Nullable Network network) {
        if (System.currentTimeMillis() < lastBroadcast + delayBetweenBroadcasts) {
            Log.i("LanLinkProvider", "onNetworkChange: relax cowboy");
            return;
        }
        lastBroadcast = System.currentTimeMillis();

        broadcastUdpIdentityPacket(network);
        mdnsDiscovery.stopDiscovering();
        mdnsDiscovery.startDiscovering();
    }

    @Override
    public void onStop() {
        //Log.i("KDE/LanLinkProvider", "onStop");
        listening = false;
        mdnsDiscovery.stopAnnouncing();
        mdnsDiscovery.stopDiscovering();
        try {
            tcpServer.close();
        } catch (Exception e) {
            Log.e("LanLink", "Exception", e);
        }
        try {
            udpServer.close();
        } catch (Exception e) {
            Log.e("LanLink", "Exception", e);
        }
    }

    @Override
    public String getName() {
        return "LanLinkProvider";
    }

    @Override
    public int getPriority() { return 20; }

}