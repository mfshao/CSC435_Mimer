/*--------------------------------------------------------

1. Mingfei Shao / 10/19/2016:

2. Java version used: build 1.8.0_102-b14

3. Precise command-line compilation examples / instructions:
> javac -cp "[PATH_OF_sourcecode];[PATH_OF_xstream-1.2.1.jar];[PATH_OF_xpp3_min-1.1.3.4.O.jar]" MyWebServer.java
or use the batch file. You need to configure the path information in the batch file accordingly at first.
> jcx.bat

4. Precise examples / instructions to run this program:
In separate shell windows:
> set classpath=%classpath%[PATH_OF_sourcecode];[PATH_OF_xstream-1.2.1.jar];[PATH_OF_xpp3_min-1.1.3.4.O.jar]
> java MyWebServer
The program will use the default port numbers (2540 and 2570).
or use the batch file. You need to configure the path information in the batch file accordingly at first.
> rx.bat

5. List of files:
a. MyWebServer.java
b. BCHandler.java
c. mimer-discussion.html
d. checklist-mimer.html
e. serverlog.txt

6. Notes:
Make sure you have a .xyz file in the root folder of this server program. Otherwise it will send a 404 Not Found error message to the browser.
I also discovered a problem caused by the inconstancy of line separators of different operating systems.
For example, the mimer-data.xyz is saved in UNIX format, which means it uses only line feed "\n" as separators. So the total size of mimer-data.xyz is 101.
However, for a browser running on Windows system, it will use DOS/Windows format to save the temp file, which means it will use crlf "\r\n" as separators.
As the result, the DOS/Windows format mimer-data.xyz should have a size of 106 rather than 101.
This inconstancy will cause trouble so the temp file may not have complete content or have some extra content.
I'm using some code snippet from http://stackoverflow.com/questions/3066511/how-to-determine-file-format-dos-unix-mac to address this problem.
My code now will read through the file to determine its system format, use appropriate line separators depending on the client side, and add or subtract file length accordingly.
The idea is the difference caused by the line separators is equal to the number of lines in the file.
For detailed workflow of this program, please refer to mimer-discussion.html.

----------------------------------------------------------*/

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import com.thoughtworks.xstream.XStream;

// Customized DataArray class to store the data read from the input file
class myDataArray {
    // Total number of lines stored
    int num_lines = 0;
    // An array of strings to store the actual lines of data
    String[] lines = new String[8];
}

// Back Channel (BC) Worker class to handle back channel connections, each worker class will run on a new thread
class BCWorker extends Thread {
    private Socket sock;

    BCWorker(Socket s) {
        sock = s;
    }

    // Initialize XStream object for XML parsing methods
    private XStream xstream = new XStream();
    // Get the new line symbol which can differ from different operating systems
    private final String newLine = System.getProperty("line.separator");


    // Define the behavior of a running thread
    public void run() {
        // String to hold the all content from XML file
        String xml;
        // String to hold the content that is currently being read out from XML file
        String temp;
        // Declare a customized myDataArray object to hold the de-serialized object from XML content
        myDataArray da;

        PrintStream out;
        BufferedReader in;

        // Formatting console output
        System.out.println();
        System.out.println("======== Begin BC Looper response ========");
        System.out.println("Called BC worker.");
        try {
            // Initialize input reader to read from the input stream, which is the marshaled XML content received via back channel from BCHandler
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            // Initialize output writer for sending ack messages back to BCHandler
            out = new PrintStream(sock.getOutputStream());
            // Initialize string to hold the all content from XML file
            xml = "";
            // Read XML content from the input reader
            while (true) {
                // Read a line from input reader
                temp = in.readLine();
                // If encountered "end_of_xml" signal message, then all the content of XML file has been received, break from the reading loop
                if (temp.indexOf("end_of_xml") > -1) break;
                    // If not, then the current line of content still belongs to the XML file, append it to the string to hold the all content from XML file and start a new line
                else xml = xml + temp + newLine;
            }
            // Print out marshaled XML data onto console
            System.out.println("The XML marshaled data:");
            System.out.println(xml);
            // Send ack messages back to BCHandler
            out.println("Acknowledging Back Channel Data Receipt"); // send the ack
            out.flush();
            // Close output writer
            sock.close();

            // Use XStream.fromXML(String xml) method to de-serialize the XML data into its original format of object, which is a myDataArray object in this case
            da = (myDataArray) xstream.fromXML(xml);
            System.out.println("Here is the restored data: ");
            // Print out unmarshaled XML data onto console, using the index variable i to control the print of a string array
            for (int i = 0; i < da.num_lines; i++) {
                System.out.println(da.lines[i]);
            }
            // Formatting console output
            System.out.println("======== End BC Looper response ========");
            System.out.println();
        } catch (IOException ioe) {
        }
    }
}

// Define a new class that is runnable by a thread. This class serves as the back channel server. It will be initialized by MyWebServer and running simultaneously with MyWebServer on different threads.
class BCLooper implements Runnable {
    public void run() {
        // Not interesting. Number of requests for OpSys to queue
        int q_len = 6;
        // Back channel server will be using port 2570
        int port = 2570;
        Socket sock;

        // Print server info
        System.out.println("Mingfei Shao's BC Looper starting up, listening at port " + port + ".\n");

        try {
            // Initialize a new server type socket using port number and queue length
            ServerSocket servSock = new ServerSocket(port, q_len);
            // Stick here to serve any incoming back channel client connections
            while (true) {
                // Wait for back channel client to connect
                sock = servSock.accept();
                // After connected, start a new worker thread to handle client's connection, and main thread stays in the loop, waiting for next client
                new BCWorker(sock).start();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}

// Worker class to handle HTTP requests from browser, each worker class will run on a new thread
class ServerWorker extends Thread {
    private Socket sock;
    // Get the carriage return / line feed combination
    private String crlf = HtmlUtil.getCRLF();

    ServerWorker(Socket s) {// Constructor to initialize socket
        sock = s;
    }

    // Compose error message into html format
    private String composeHttpError(String title, String h1, String text) {
        // Construct message head by append title tag to message title
        String head = HtmlUtil.appendTitle(title);
        // Append head tag to message title
        head = HtmlUtil.appendHead(head);
        // Construct message body by append p and h1 tags to message text
        String body = HtmlUtil.appendH1(h1) + HtmlUtil.appendP(text);
        // Append body tag to message body
        body = HtmlUtil.appendBody(body);
        // Concatenate message head and message body, append html tag and return
        return HtmlUtil.appendHtml(head + body);
    }

    // Send HTTP error message out
    private void sendHttpError(int code, String filePath, PrintStream out) {
        // Message header
        String header;
        // Message body
        String message;

        // Compose different header and body according to error code
        switch (code) {
            // Bad Request
            case 400:
                header = "HTTP/1.1 400 Bad Request";
                message = composeHttpError("400 Bad Request", "Bad Request", "Your browser sent a request that this server could not understand.");
                break;
            // Forbidden
            case 403:
                header = "HTTP/1.1 403 Forbidden";
                message = composeHttpError("403 Forbidden", "Forbidden", "You don't have permission to access " + filePath + " on this server.");
                break;
            // Not Found
            case 404:
                header = "HTTP/1.1 404 Not Found";
                message = composeHttpError("404 Not Found", "Not Found", "The requested URL " + filePath + " was not found on this server.");
                break;
            // Just in case, can be ignored
            default:
                header = "";
                message = "";
        }

        // Send composed HTTP error message out
        sendHttpMessage(header, Integer.toString(message.length()), "text/html", message, out);
    }

    // Method to send HTTP message
    private void sendHttpMessage(String header, String contentLen, String contentType, String content, PrintStream out) {
        // Print header + crlf + content length + crlf + content type, followed by two crlfs and then the message content as convention
        out.print(header);
        out.print(crlf);
        out.print("Content-Length: " + contentLen);
        out.print(crlf);
        out.print("Content-Type: " + contentType);
        out.print(crlf);
        out.print(crlf);
        out.print(content);
        // Flush the output stream for safe
        out.flush();

        printServerConsoleMessage(header, contentLen, contentType, content);
    }

    // Method used to print out debug message on server console, can be ignored or commented out
    private void printServerConsoleMessage(String header, String contentLen, String contentType, String content) {
        System.out.println();
        System.out.println("======== Begin server reply ========");
        System.out.print(header);
        System.out.print(crlf);
        System.out.print("Content-Length: " + contentLen);
        System.out.print(crlf);
        System.out.print("Content-Type: " + contentType);
        System.out.print(crlf);
        System.out.print(crlf);
        System.out.print(content);
        System.out.println("======== End server reply ========");
        System.out.println();
    }

    // Method to send HTTP message in binary format (e.g.: images)
    private void sendHttpBinaryMessage(String header, String contentLen, String contentType, byte[] content, PrintStream out) {
        // Print header + crlf + content length + crlf + content type, followed by two crlfs and then the binary content as convention
        out.print(header);
        out.print(crlf);
        out.print("Content-Length: " + contentLen);
        out.print(crlf);
        out.print("Content-Type: " + contentType);
        out.print(crlf);
        out.print(crlf);
        // Use PrintStream.write() method to write binary data
        try {
            out.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Flush the output stream for safe
        out.flush();

        printServerConsoleMessage(header, contentLen, contentType, "[Binary data]" + crlf);
    }

    // Method to send the favicon.ico to browser
    private void sendFavIco(File file, String contentType, PrintStream out) {
        try {
            // Read binary data out from the image file favicon.ico
            DataInputStream dis = new DataInputStream(new FileInputStream(file));
            // Byte buffer to hold binary content
            byte[] buffer = new byte[dis.available()];
            dis.readFully(buffer);
            dis.close();
            sendHttpBinaryMessage("HTTP/1.1 200 OK", Long.toString(file.length()), contentType, buffer, out);
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    // If the file requested by GET can be found on server side
    private void sendFileOK(File file, HtmlUtil.SystemType clientSystemType, PrintStream out) {
        // Temp string to hold content type information
        String contentType = "";
        // Initialize server side system type
        HtmlUtil.SystemType serverSystemType = HtmlUtil.SystemType.UNKNOWN;
        // Initialize server side raw file length
        Long fileLen = file.length();

        // Assign content type information by file extension
        if (file.getName().toLowerCase().endsWith(".txt")) {
            // Plain text file
            contentType = "text/plain";
        } else if (file.getName().toLowerCase().endsWith(".html") || file.getName().toLowerCase().endsWith(".htm")) {
            // Html file
            contentType = "text/html";
        } else if (file.getName().toLowerCase().endsWith(".xyz")) {
            // xyz file
            contentType = "application/xyz";
        } else if (file.getName().equalsIgnoreCase("favicon.ico")) {
            // Favicon.ico file, has special MIME type
            contentType = "image/x-icon";
            sendFavIco(file, contentType, out);
            return;
        }

        // Read out file content
        StringBuilder sb = new StringBuilder();
        String textFromFile = "";
        // Initialize number of lines in the file
        int lines = 0;

        try {
            // Read through the file to decide its format by identifying line separator format
            BufferedReader in = new BufferedReader(new FileReader(file));
            int c;
            while ((c = in.read()) != -1) {
                switch (c) {
                    // If starts with "\n"
                    case HtmlUtil.LFchar:
                        serverSystemType = HtmlUtil.SystemType.UNIX;
                        break;
                    // If starts with "\r"
                    case HtmlUtil.CRchar: {
                        // If follows by "\n"
                        if (in.read() == HtmlUtil.LFchar) {
                            serverSystemType = HtmlUtil.SystemType.WINDOWS;
                        } else {
                            serverSystemType = HtmlUtil.SystemType.MAC;
                        }
                    }
                    default:
                        continue;
                }
            }

            // Reset the BufferedReader to the head of the file
            in = new BufferedReader(new FileReader(file));
            // If not done reading
            while ((textFromFile = in.readLine()) != null) {
                // Calculate number of lines
                lines++;
                // Read a line out from file, append corresponding line separators to it
                sb.append(textFromFile);
                switch (clientSystemType) {
                    case WINDOWS:
                        sb.append(crlf);
                        break;
                    case UNIX:
                        sb.append(HtmlUtil.getLF());
                        break;
                    case MAC:
                        sb.append(HtmlUtil.getCR());
                        break;
                    case UNKNOWN:
                        sb.append(HtmlUtil.getLF());
                        break;
                }
            }
            // Full file content with crlfs at the end of each line
            textFromFile = sb.toString();

            // If the client is expecting DOS/Windows format and server side has different format, increase file size
            if ((clientSystemType == HtmlUtil.SystemType.WINDOWS) && (clientSystemType != serverSystemType)) {
                fileLen += lines;
            }
            // If the server side is using DOS/Windows format and client side is expecting different format, decrease file size
            else if ((serverSystemType == HtmlUtil.SystemType.WINDOWS) && (clientSystemType != serverSystemType)) {
                fileLen -= lines;
            }
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        // Send successful message and file content back
        sendHttpMessage("HTTP/1.1 200 OK", Long.toString(fileLen), contentType, textFromFile, out);
    }

    // If the directory requested by GET can be found on server side
    private void sendDirOK(ArrayList<String> resultList, String path, PrintStream out) {
        // Construct directory and file list in html format
        StringBuilder sb = new StringBuilder();

        // Heading line
        sb.append(HtmlUtil.appendH1("Index of " + path));
        // The contents
        for (int i = 0; i < resultList.size(); i++) {
            if (i == 0) {
                // First path in resultList is the Parent Directory
                sb.append(HtmlUtil.appendHref("Parent Directory", resultList.get(i)));
            } else {
                // For everything else, their display name and path are the same as saved in resultList
                sb.append(HtmlUtil.appendHref(resultList.get(i), resultList.get(i)));
            }
        }
        String content = sb.toString();
        // Append pre tag to message
        content = HtmlUtil.appendPre(content);
        // Append html tag to message
        content = HtmlUtil.appendHtml(content);

        // Send successful message and constructed html information back
        sendHttpMessage("HTTP/1.1 200 OK", Integer.toString(content.length()), "text/html", content, out);
    }

    // Method to iterate through current folder and to gather information for files and directories, results are stored in resultList
    private void listDir(File file, ArrayList<String> resultList) {
        File[] strFilesDirs = file.listFiles();

        // If the directory is not empty
        if (strFilesDirs != null) {
            // Iterate through files and directories
            for (File f : strFilesDirs) {
                // Current entry is a directory
                if (f.isDirectory()) {
                    // Add "/" at the end of its name
                    resultList.add(f.getName() + "/");
                } else {
                    // This is a file, just get its name out
                    resultList.add(f.getName());
                }
            }
        }
    }

    // Method to handle the (fake) CGI request
    private void addnum(String paraAll, PrintStream out) {
        // Split the three arguments out
        String[] paraStr = paraAll.split("&");
        // Get the values of three arguments
        String personStr = paraStr[0].substring(paraStr[0].indexOf("=") + 1);
        String num1Str = paraStr[1].substring(paraStr[1].indexOf("=") + 1);
        String num2Str = paraStr[2].substring(paraStr[2].indexOf("=") + 1);
        int num1 = 0;
        int num2 = 0;

        // If any of the arguments is invalid, we have error message
        String errorMessage = "";
        if (personStr.isEmpty()) {
            // No person name entered
            errorMessage += "Please enter a valid person name. ";
        }
        try {
            num1 = Integer.parseInt(num1Str);
        } catch (NumberFormatException nfe) {
            // Parse num1 fails
            errorMessage += "Please enter a valid integer for num1. ";
        }
        try {
            num2 = Integer.parseInt(num2Str);
        } catch (NumberFormatException nfe) {
            // Parse num2 fails
            errorMessage += "Please enter a valid integer for num2. ";
        }

        // If found error message, then something is wrong
        if (!errorMessage.isEmpty()) {
            // Construct error message into html format, append pre and html tags
            errorMessage = HtmlUtil.appendPre(errorMessage);
            errorMessage = HtmlUtil.appendHtml(errorMessage);
            // Send error message out
            sendHttpMessage("HTTP/1.1 200 OK", Integer.toString(errorMessage.length()), "text/html", errorMessage, out);
        } else {
            // If no error message, we are good to go, add num1 and num2 together
            int addResult = num1 + num2;
            // Construct success message with person name, num1, num2 and the result after addition
            String successMessage = "Dear " + personStr.replace("+", " ") + ", the sum of " + num1Str + " and " + num2Str + " is " + Integer.toString(addResult) + ".";
            // Construct success message into html format, append pre and html tags
            successMessage = HtmlUtil.appendPre(successMessage);
            successMessage = HtmlUtil.appendHtml(successMessage);
            // Send success message out
            sendHttpMessage("HTTP/1.1 200 OK", Integer.toString(successMessage.length()), "text/html", successMessage, out);
        }
    }

    // Method to handle the HTTP GET request
    private void processGetRequest(String filePath, HtmlUtil.SystemType sysType, PrintStream out) {
        // Add "." in front of the path in GET request to start from current directory.
        filePath = "." + filePath;
        File file = new File(filePath);

        // A little bit security measure (latest version of Firefox and Chrome will actually take care of ../.. at browser side)
        if (filePath.contains("../..")) {
            // Someone is trying too peek around, send 403 error and not serving this request
            sendHttpError(403, filePath, out);
            return;
        }

        // If the GET request comes from the CGI form
        if (filePath.contains("cgi/addnums.fake-cgi")) {
            // Split the string by question mark
            String[] subStr = filePath.split("\\?");
            // Parameters are in the substring immediately following the question mark
            addnum(subStr[1], out);
        } else {
            // If the GET request asks for a folder
            if (filePath.endsWith("/")) {
                // If the folder exists
                if (file.exists()) {
                    // Construct an ArrayList to hold item information in the folder
                    ArrayList<String> resultList = new ArrayList<>();
                    // First element in the ArrayList is the path of parent directory
                    if (file.getParent() != null) {
                        // If the folder is not the root of the server, use "../" to go back a level
                        resultList.add("../");
                    } else {
                        // If the folder is the root of the server, restrict the parent directory to be itself so it will not go over the limited area
                        resultList.add("./");
                    }
                    // Call method to list over items in this directory
                    listDir(file, resultList);
                    // Send the directory information for processing (generating the html page)
                    sendDirOK(resultList, filePath, out);
                } else {
                    // If the requested folder does not exists, send 404 error out
                    sendHttpError(404, filePath, out);
                }
            } else {
                // In this case, looking for a file
                if (file.exists()) {
                    if (file.isFile()) {
                        // If file exists and is a file, then call method to process the content of the file and send out
                        sendFileOK(file, sysType, out);
                    } else {
                        // If it is not a file, something is wrong, send 403 error just for cautious
                        sendHttpError(403, filePath, out);
                    }
                } else {
                    // File not found, send 404 error out
                    sendHttpError(404, filePath, out);
                }
            }
        }
    }

    // Define the behavior of a running thread
    public void run() {
        PrintStream out;
        BufferedReader in;
        try {
            // Initialize the input stream of the socket as BufferedReader
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            // Initialize the output stream of the socket as PrintStream
            out = new PrintStream(sock.getOutputStream());
            // Hold the request string
            String request;
            String[] subStr = {};
            // Initialize client side system type
            HtmlUtil.SystemType clientSystemType = HtmlUtil.SystemType.UNKNOWN;

            try {
                // Read request from client (browser)
                while (!(request = in.readLine()).isEmpty()) {
                    System.out.println(request);
                    // If it is a GET request (not taking consideration into HTTP protocol version or Host info at this time to simplify the problem)
                    if (request.contains("GET")) {
                        // Split the request string by white spaces
                        subStr = request.split("\\s+");
                    }

                    // Look for the value of "User-Agent" field
                    if (request.contains("User-Agent")) {
                        if (request.toUpperCase().contains("WINDOWS")) {
                            // If it is running Windows
                            clientSystemType = HtmlUtil.SystemType.WINDOWS;
                        } else if (request.toUpperCase().contains("MAC")) {
                            // If it is running macOS
                            clientSystemType = HtmlUtil.SystemType.MAC;
                        } else if (request.toUpperCase().contains("UNIX") || request.toUpperCase().contains("LINUX")) {
                            // If it is running Linux or UNIX
                            clientSystemType = HtmlUtil.SystemType.UNIX;
                        }
                    }
                }
                // If the HTTP GET request has arguments
                if (subStr.length > 1) {
                    // The string after the first white space is the path the server is trying to get
                    String filePath = subStr[1];
                    // Call method to handle GET request
                    processGetRequest(filePath, clientSystemType, out);
                } else {
                    // The HTTP GET request is invalid, send 400 error out
                    sendHttpError(400, null, out);
                }
            } catch (IOException ioe) {
                // In case of read from input stream fails
                System.out.println("Server read error");
                ioe.printStackTrace();
            } catch (NullPointerException npe) {
            }
            System.out.println();
            // Close everything
            out.close();
            in.close();
            sock.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}

// Helper class for constructing html pages
class HtmlUtil {
    // Define different system type
    public enum SystemType {
        WINDOWS, UNIX, MAC, UNKNOWN
    }

    // Define single cr here (MAC)
    static final char CRchar = '\r';
    // Define single lf here (UNIX)
    static final char LFchar = '\n';
    // Define string version
    private static final String CR = Character.toString(CRchar);
    private static final String LF = Character.toString(LFchar);
    private static final String CRLF = Character.toString(CRchar) + Character.toString(LFchar);

    // Getter for cr
    static String getCR() {
        return CR;
    }

    // Getter for lf
    static String getLF() {
        return LF;
    }

    // Getter for crlf
    static String getCRLF() {
        return CRLF;
    }

    // Append html tag <title></title>
    static String appendTitle(String str) {
        return "<title>" + CRLF + str + "</title>" + CRLF;
    }

    // Append html tag <head></head>
    static String appendHead(String str) {
        return "<head>" + CRLF + str + "</head>" + CRLF;
    }

    // Append html tag <body></body>
    static String appendBody(String str) {
        return "<body>" + CRLF + str + "</body>" + CRLF;
    }

    // Append html tag <p></p>
    static String appendP(String str) {
        return "<p>" + CRLF + str + "</p>" + CRLF;
    }

    // Append html tag <html></html>
    static String appendHtml(String str) {
        return "<html>" + CRLF + str + "</html>" + CRLF;
    }

    // Append html tag <pre></pre>
    static String appendPre(String str) {
        return "<pre>" + CRLF + str + "</pre>" + CRLF;
    }

    // Append html tag <h1></h1>
    static String appendH1(String str) {
        return "<h1>" + str + "</h1>" + CRLF;
    }

    // Append html tag <a href></a>
    static String appendHref(String fileName, String filePath) {
        return "<a href=\"" + filePath + "\">" + fileName + "</a><br>" + CRLF;
    }
}

public class MyWebServer {
    // Define default port number
    private static final int DEFAULT_PORT = 2540;

    @SuppressWarnings("resource")
    public static void main(String[] args) throws IOException {
        // Create an new back channel server object
        BCLooper BCL = new BCLooper();
        // Create a new thread to run the back channel server
        Thread t = new Thread(BCL);
        // Execute the back channel server thread
        t.start();

        // Not interesting. Number of requests for OpSys to queue
        int q_len = 6;
        // Initialize port number to default
        int port = DEFAULT_PORT;

        Socket sock;
        // Initialize a new server type socket using port number and queue length
        ServerSocket servSock = new ServerSocket(port, q_len);
        // Print server info
        System.out.println("Mingfei Shao's MyWebServer starting up, listening at port " + port + ".\n");
        // Stick here to serve any incoming clients
        while (true) {
            // Wait for client to connect
            sock = servSock.accept();
            // After connected, start a new worker thread to handle client's request, and main thread stays in the loop, waiting for next client
            new ServerWorker(sock).start();
        }
    }
}
