import java.io.*;
import java.net.Socket;

public class SocketWrapper {  //public class used for input and output data with socket

    public static void sendData(Socket socket, String data) throws IOException {
        OutputStream outStream = socket.getOutputStream();
        outStream.write(data.getBytes());
        // System.out.println("DEBUG: send " + data);
    }

    public static String receiveData(Socket socket) throws IOException {
        InputStream inputStream = socket.getInputStream();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line = bufferedReader.readLine();
        // System.out.println("DEBUG receive: " + line);
        return line;
    }

    public static void closeSocket(Socket socket) throws IOException {
        socket.shutdownOutput();
        socket.shutdownInput();
        socket.close();
    }

}
