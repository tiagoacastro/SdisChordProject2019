import javafx.util.Pair;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class ChordInfo implements Runnable{
    public static int mBytes = 1; //hash size in bytes
    public static BigInteger peerHash;
    private static ArrayList<ConnectionInfo> fingerTable = new ArrayList<>(mBytes * 8);
    public static ConnectionInfo predecessor = null;

    ChordInfo() throws UnknownHostException {
        try {
            setChord();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public static ConnectionInfo getPredecessor() {
        return predecessor;
    }

    public static ArrayList<ConnectionInfo> getFingerTable() {
        return fingerTable;
    }

    public static void addEntry(BigInteger hashedID, String address, int port) {
        fingerTable.add(new ConnectionInfo(hashedID, address, port));
        printFingerTable();
    }

    /**
     * Calls functions to create hash and fill finger table
     */
    private void setChord() throws UnknownHostException {

        ChordInfo.peerHash = BigInteger.valueOf(Integer.parseInt(getPeerHash(mBytes, Peer.port),16));

        System.out.println("Peer hash = " + peerHash + "\n");

        //se não for o primeiro peer no sistema
        if(Peer.connectionInfo.getPort() != 0) {
            Peer.executor.submit(new SuccessorRequest(Peer.connectionInfo.getPort(), Peer.port));
        }
        initFingerTable();
        printFingerTable();
    }

    private void initFingerTable() throws UnknownHostException {
        for(int i = 0 ; i < mBytes * 8;  i++) {
            fingerTable.add(new ConnectionInfo(peerHash,InetAddress.getLocalHost().getHostAddress(),Peer.port ));
        }
    }

    public static void printFingerTable() {
        System.out.println("FingerTable");

        for(int i = 0; i < fingerTable.size(); i++){
            System.out.println(fingerTable.get(i).getHashedKey() + " : " +fingerTable.get(i).getIp() + " : " + fingerTable.get(i).getPort());
        }
    }

    /**
     * Creates hash with size hashSize from server's port
     *
     * @param hashSize hash size
     * @return hash
     */
    public static String getPeerHash(int hashSize, int port)
    {
        String originalString;
        MessageDigest md = null;
        StringBuilder result = new StringBuilder();

        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error getting instance of MessageDigest");
            System.exit(1);
        }

        originalString = "" + port;

        md.update(originalString.getBytes());
        byte[] hashBytes = md.digest();

        byte[] trimmedHashBytes = Arrays.copyOf(hashBytes, hashSize);

        for (byte byt : trimmedHashBytes)
            result.append(Integer.toString((byt & 0xff) + 0x100, 16).substring(1));

        return result.toString();
    }

    /**
     * Fills initial finger table
     *
     * @param fingerTable struct to be filled
     * @param numberEntries finger table size (hash size in bits)
     * @param referencedPort port of existing peer that was passed by argument
     */

    private void getFingerTable(ArrayList<String> fingerTable, int numberEntries, int referencedPort) {
        BigInteger hashBI = ChordInfo.peerHash;

        for(int i = 0; i < numberEntries; i++)
        {
            String nextKey = calculateNextKey(hashBI, i, numberEntries);
            //getSuccessor(referencedPort, nextKey);
            // se não existir peer com esta chave (K), o peer responsável vai ser aquele com menor chave >= K
        }
    }

    /**
     * Calculates the key of the next entry of the fingertable - ( hash + 2^index) mod 2^m
     *
     * @param hash peer's hash
     * @param index finger table's index to be filled
     * @param m hash size (bits)
     * @return next key's hash
     */
    private String calculateNextKey(BigInteger hash, int index, int m)
    {
        //Exemplo
        // hash = 10, index = 0, m = 7 => 10 + 2^0 = 11
        // hash = 10, index = 3, m = 7 => 10 + 2^3 = 18
        // hash = 125, index = 3, m = 7 => 125 + 2^3 = 133 mod 2^7 = 8

        BigInteger add = new BigInteger(String.valueOf((int) Math.pow(2, index)));
        BigInteger mod =  new BigInteger(String.valueOf((int) Math.pow(2, m)));

        BigInteger res = hash.add(add).mod(mod);
        return res.toString(16);
    }


    //NOT TESTED !!
    public static void searchSuccessor(ConnectionInfo senderInfo)
    {
        String message;
        String parameters[];

        BigInteger hashedKey = fingerTable.get(0).getHashedKey();

        /*Se o node S que enviou a mensagem, e sendo N o node que a recebeu, se encontrar em [N,sucessor(N)]
        então sucessor(S) = sucessor(N)*/
        if(senderInfo.getHashedKey().compareTo(peerHash) == 1) {
            if (senderInfo.getHashedKey().compareTo(hashedKey) == -1) {
                parameters = new String[]{ peerHash.toString(), fingerTable.get(0).getIp(), String.valueOf(fingerTable.get(0).getIp())};
                message = Auxiliary.addHeader("SUCCESSOR", parameters);
                Auxiliary.sendMessage(message, senderInfo.getIp(), senderInfo.getPort() );
            }
        }

        /*Se a condição anterior não acontecer, então vai-se procurar o predecessor com a chava mais alta,
          mas que seja menor que a chave do node que enviou a mensagem*/
        else {
            for(int i = fingerTable.size()-1; i >= 0; i--){
                if(fingerTable.get(i).getHashedKey().compareTo(peerHash) == 1)
                    if (fingerTable.get(i).getHashedKey().compareTo(senderInfo.getHashedKey()) == -1) {
                        parameters = new String[]{senderInfo.getHashedKey().toString(), senderInfo.getIp(), String.valueOf(senderInfo.getPort())};
                        message = Auxiliary.addHeader("LOOKUP", parameters);
                        Auxiliary.sendMessage(message, fingerTable.get(i).getIp(), fingerTable.get(i).getPort());
                    }
            }

            parameters = new String[]{predecessor.toString(),senderInfo.getIp(), String.valueOf(senderInfo.getPort())};
            message = Auxiliary.addHeader("SUCCESSOR", parameters);
            Auxiliary.sendMessage(message, fingerTable.get(fingerTable.size()-1).getIp(), fingerTable.get(fingerTable.size()-1).getPort());
        }
    }

    /*
    public static void setSuccessor(String key)
    {
        if(fingerTable.size() == 0) {
            fingerTable.add(key);
        }

        fingerTable.set(0,key);
    }
*/
    @Override
    public void run() {

    }
}
