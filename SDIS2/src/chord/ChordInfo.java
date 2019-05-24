package chord;

import messages.MessageForwarder;
import peer.FixFingers;
import peer.Peer;
import peer.SuccessorRequest;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

public class ChordInfo implements Runnable{
    private final static int mBytes = 1; //hash size in bytes
    public static BigInteger peerHash;
    private static ArrayList<ConnectionInfo> fingerTable = new ArrayList<>(mBytes * 8);
    public static ConnectionInfo predecessor = null;

    public ChordInfo(){
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

    public static int getM(){ return mBytes;}

    /**
     * Calls functions to create hash and fill finger table
     */
    private void setChord() throws UnknownHostException {

        ChordInfo.peerHash = BigInteger.valueOf(Integer.parseInt(getPeerHash(mBytes, Peer.port),16));
        System.out.println("peer.Peer hash = " + peerHash + "\n");

        //se não for o primeiro peer no sistema
        if(Peer.connectionInfo.getPort() != 0) {
           // Peer.executor.submit(new SuccessorRequest(Peer.connectionInfo.getPort(), Peer.port));
        }

        /*for(int i = 0; i < mBytes * 8; i++) {
            String hash = calculateNextKey(peerHash, i, mBytes * 8);
            fingerTable.add(new ConnectionInfo(new BigInteger(hash), InetAddress.getLocalHost().getHostAddress(), Peer.port));
        }*/

        initFingerTable();
        printFingerTable();

        FixFingers ff = new FixFingers();
        Peer.executor.submit(ff);
    }

    private void initFingerTable() throws UnknownHostException {
            fingerTable.add(new ConnectionInfo(peerHash,InetAddress.getLocalHost().getHostAddress(), Peer.port ));
    }

    public static void printFingerTable() {
        System.out.println("FingerTable");

        for(ConnectionInfo finger : fingerTable){
            System.out.println(finger.getHashedKey() + " : " + finger.getIp() + " : " + finger.getPort());
        }
    }

    /**
     * Creates hash with size hashSize from server's port
     *
     * @param hashSize hash size
     * @return hash
     */
    private static String getPeerHash(int hashSize, int port)
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
     * Calculates the key of the next entry of the fingertable - ( hash + 2^index) mod 2^m
     *
     * @param hash peer's hash
     * @param index finger table's index to be filled
     * @param m hash size (bits)
     * @return next key's hash
     */
    public static String calculateNextKey(BigInteger hash, int index, int m)
    {
        //Exemplo
        // hash = 10, index = 0, m = 7 => 10 + 2^0 = 11
        // hash = 10, index = 3, m = 7 => 10 + 2^3 = 18
        // hash = 125, index = 3, m = 7 => 125 + 2^3 = 133 mod 2^7 = 8

        BigInteger add = new BigInteger(String.valueOf((int) Math.pow(2, index)));
        BigInteger mod =  new BigInteger(String.valueOf((int) Math.pow(2, m)));

        BigInteger res = hash.add(add).mod(mod);
        return res.toString();
    }


    //NOT TESTED !!
    public static void searchSuccessor(ConnectionInfo senderInfo)
    {
        String message;
        String parameters[];

        BigInteger successorKey = fingerTable.get(0).getHashedKey();

        /*Se o node S que enviou a mensagem, e sendo N o node que a recebeu, se encontrar em [N,sucessor(N)]
        então sucessor(S) = sucessor(N)*/
        if(numberInInterval(peerHash, successorKey, senderInfo.getHashedKey())) {
                parameters = new String[]{senderInfo.getHashedKey().toString(), successorKey.toString(), fingerTable.get(0).getIp(), String.valueOf(fingerTable.get(0).getPort())};
                message = MessageForwarder.addHeader("SUCCESSOR", parameters);
                MessageForwarder.sendMessage(message, senderInfo.getIp(), senderInfo.getPort() );
        }

        /*Se a condição anterior não acontecer, então vai-se procurar o predecessor com a chava mais alta,
          mas que seja menor que a chave do node que enviou a mensagem*/
        else {
            for(int i = fingerTable.size()-1; i >= 0; i--){

                if(numberInInterval(peerHash, senderInfo.getHashedKey(), fingerTable.get(i).getHashedKey())) {
                        parameters = new String[]{senderInfo.getHashedKey().toString(), senderInfo.getIp(), String.valueOf(senderInfo.getPort())};
                        message = MessageForwarder.addHeader("LOOKUP", parameters);
                        MessageForwarder.sendMessage(message, fingerTable.get(i).getIp(), fingerTable.get(i).getPort());
                }
            }

            try {
                parameters = new String[]{senderInfo.getHashedKey().toString(), ChordInfo.peerHash.toString(), InetAddress.getLocalHost().getHostAddress(), String.valueOf(Peer.port)};
                message = MessageForwarder.addHeader("SUCCESSOR", parameters);
                MessageForwarder.sendMessage(message, senderInfo.getIp(), senderInfo.getPort());
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
    }

    public static String searchSuccessor2(ConnectionInfo senderInfo)
    {
        String parameters[];

        BigInteger successorKey = fingerTable.get(0).getHashedKey();

        System.out.println(senderInfo.getHashedKey() + " [ " + peerHash + ", " + successorKey + " ]");
        if(numberInInterval(peerHash, successorKey, senderInfo.getHashedKey())) {
            parameters = new String[]{successorKey.toString(), fingerTable.get(0).getIp(), String.valueOf(fingerTable.get(0).getPort())};
            System.out.println("Sucessor");
            return MessageForwarder.addHeader("SUCCESSOR", parameters);
        }

        else {
            for(int i = fingerTable.size()-1; i >= 0; i--){

                if(fingerTable.get(i).getHashedKey() == null)
                    continue;

                if(numberInInterval(peerHash, senderInfo.getHashedKey(), fingerTable.get(i).getHashedKey())) {
                    parameters = new String[]{fingerTable.get(i).getIp(), String.valueOf(fingerTable.get(i).getPort())};
                    return MessageForwarder.addHeader("LOOKUP", parameters);
                }
            }

            System.out.println("Proprio");

            try {
                parameters = new String[]{ChordInfo.peerHash.toString(), InetAddress.getLocalHost().getHostAddress(), String.valueOf(Peer.port)};
                return MessageForwarder.addHeader("SUCCESSOR", parameters);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }

        return "";
    }


    private static boolean numberInInterval(BigInteger begin, BigInteger end, BigInteger value)
    {
        boolean result = false;
        int cmp = begin.compareTo(end);

        if(cmp == 1) {
            if (value.compareTo(begin) == 1 || value.compareTo(end) == -1)
                result = true;
        }

        else if (cmp == -1) {
            if (value.compareTo(begin) == 1 && value.compareTo(end) == -1)
                result = true;
        }

        else {
            if (value.compareTo(begin) == 0)
                result = true;
        }

        return result;
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
