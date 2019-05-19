import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


public class PeerReceiver implements Runnable {
    private SSLServerSocket serverSocket;
    private int port;

    PeerReceiver (int port) {
        this.port = port;
        setJSSE();
        setServerSocket();
    }

    /**
     * Configures the keystore and truststore
     */
    private static void setJSSE() {
        System.setProperty("javax.net.ssl.keyStore", "server.keys");
        System.setProperty("javax.net.ssl.keyStorePassword", "123456");
        System.setProperty("javax.net.ssl.trustStore", "truststore");
        System.setProperty("javax.net.ssl.trustStorePassword", "123456");
    }

    /**
     * Creates server socket
     */
    private void setServerSocket()
    {
        serverSocket = null;

        SSLServerSocketFactory serverSocketFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();

        try {
            serverSocket = (SSLServerSocket) serverSocketFactory.createServerSocket(port);
            serverSocket.setNeedClientAuth(true);
            serverSocket.setEnabledProtocols(serverSocket.getSupportedProtocols());
            System.out.println("Server socket thread created and ready to receive");
        } catch (IOException e) {
            System.err.println("Error creating server socket");
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        String clientSentence = null;
        SSLSocket connectionSocket = null;

        //Ciclo infinito para receber mensagens no serverSocket
        while(true) {
            try {
                connectionSocket = (SSLSocket) serverSocket.accept();
            } catch (IOException e) {
                e.printStackTrace();
            }
            BufferedReader inFromClient = null;

            try {
                inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                clientSentence = inFromClient.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println("Received: " + clientSentence);
        }
    }
}