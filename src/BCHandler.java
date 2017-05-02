/*--------------------------------------------------------

1. Mingfei Shao / 10/20/2016:

2. Java version used: build 1.8.0_102-b14

3. Precise command-line compilation examples / instructions:
> javac -cp "[PATH_OF_sourcecode];[PATH_OF_xstream-1.2.1.jar];[PATH_OF_xpp3_min-1.1.3.4.O.jar]" BCHandler.java
or use the batch file. You need to configure the path information and change the .java file name in the batch file accordingly at first.
> jcxclient.bat

4. Precise examples / instructions to run this program:
This program don't need to be executed alone. It should be automatically called from a correctly configured batch file "shim.bat".
In order to make the shim.bat work, you need to configure the path information in the batch file accordingly at first.
If you still want to run this program standalone, do:
In separate shell windows:
> set classpath=%classpath%[PATH_OF_sourcecode];[PATH_OF_xstream-1.2.1.jar];[PATH_OF_xpp3_min-1.1.3.4.O.jar]
> java BCHandler
or
> java BCHandler [serverAddr]
if you would like to specify the server address, otherwise the default server address "localhost" will be used.

5. List of files:
a. MyWebServer.java
b. BCHandler.java
c. mimer-discussion.html
d. checklist-mimer.html
e. serverlog.txt

6. Notes:
For detailed workflow of this program, please refer to mimer-discussion.html.

----------------------------------------------------------*/

import com.thoughtworks.xstream.XStream;

import java.io.*;
import java.net.Socket;
import java.util.Properties;

// Customized DataArray class to store the data read from the input file
class myDataArray {
    // Total number of lines stored
    int num_lines = 0;
    // An array of strings to store the actual lines of data
    String[] lines = new String[8];
}

// Bach channel handler class to read content from MIME type application/xyz file, marshal data into XML file and send to server via back channel
public class BCHandler {
    // Declare PrintWriter object for saving to the XML file
    private static PrintWriter toXmlOutputFile;
    // Declare File object for the XML file
    private static File xmlFile;
    // Declare BufferedReader object for reading from the MIME type application/xyz file
    private static BufferedReader fromMimeDataFile;

    // Method to send data to server via back channel
    static void sendToBC(String sendData, String serverName) {
        Socket sock;
        BufferedReader fromServer;
        PrintStream toServer;
        String textFromServer;
        try {
            // Create a new socket to connect to the server via port 2570
            sock = new Socket(serverName, 2570);
            //Initialize output stream to server
            toServer = new PrintStream(sock.getOutputStream());
            //Initialize input stream from server for ack message
            fromServer =
                    new BufferedReader(new InputStreamReader(sock.getInputStream()));

            // Send data via output stream
            toServer.println(sendData);
            // Send the "end_of_xml" string as a signal message via output stream
            toServer.println("end_of_xml");
            toServer.flush();
            // Print status notice to console
            System.out.println("XML file sent to server.");
            System.out.println("Blocking on acknowledgment from Server... ");
            // Blocking and waiting for ack message from server, can be used for implementing more advanced logic like re-transmission in the future
            textFromServer = fromServer.readLine();
            if (textFromServer != null) {
                // Print out the received message from the server (ack message in this case)
                System.out.println(textFromServer);
            }
            // Close socket connection
            sock.close();
        } catch (IOException x) {
            // Exception handling
            System.out.println("Socket error.");
            x.printStackTrace();
        }
    }

    public static void main(String args[]) {
        // String to hold server name
        String serverAddr;
        // If user did not specified server address, use default address "localhost"
        if (args.length < 1) serverAddr = "localhost";
            // otherwise, use user-specified server address in commandline argument
        else serverAddr = args[0];

        // Initialize XStream object for XML parsing methods
        XStream xstream = new XStream();
        // Initialize index variable
        int i = 0;
        // Initialize a customized myDataArray object that will be serialized into XML content
        myDataArray da = new myDataArray();
        // Declare a customized myDataArray object to hold the de-serialized object from XML content
        myDataArray daTest;

        try {
            // Print handler info
            System.out.println("Mingfei Shao's back channel handler.\n");
            System.out.println("Using server: " + serverAddr + ", Port: 2540 / 2570");
            System.out.flush();

            // Get the current system properties, including environment variables
            Properties p = new Properties(System.getProperties());
            // Get the value of environment variables "firstarg", this environment variable is passed by the -D flag of java command and it is the path of the temp file saved by the browser
            String argOne = p.getProperty("firstarg");
            // Print browser temp file path info
            System.out.println("First var is: " + argOne);

            // Get the value of the current temp directory of operating system, makes the code more portable
            String tempDir = p.getProperty("java.io.tmpdir");
            // Specify the XML file name to be used
            String XMLfileName = tempDir + "\\mimer_output.xml";

            // Initialized the BufferedReader to read data from the temp file
            fromMimeDataFile = new BufferedReader(new FileReader(argOne));
            // Read at most 8 lines of data from the temp file
            while (((da.lines[i++] = fromMimeDataFile.readLine()) != null) && i < 8) {
                // Print data to console
                System.out.println("Data is: " + da.lines[i - 1]);
            }
            // Decrease index variable i by 1 to get the total number of lines that have been read
            da.num_lines = i - 1;
            // Print number of lines to console
            System.out.println("i is: " + i);

            // Serialized the myDataArray object into XML content
            String xml = xstream.toXML(da);
            // Send serialized XML content to server via back channel
            sendToBC(xml, serverAddr);

            // Print serialized XML content to console
            System.out.println("\n\nHere is the XML version:");
            System.out.print(xml);

            // De-serialize the XML content locally for verification of correctness
            daTest = (myDataArray) xstream.fromXML(xml);
            // Print de-serialized XML content to console
            System.out.println("\n\nHere is the de-serialized data: ");
            for (i = 0; i < daTest.num_lines; i++) {
                System.out.println(daTest.lines[i]);
            }
            System.out.println("\n");

            // Initialize the File object to save the XML content into XML file on disk
            xmlFile = new File(XMLfileName);
            if (xmlFile.exists() == true && xmlFile.delete() == false) {
                // If the XML file already exists and cannot be deleted, throw an exception
                throw (IOException) new IOException("XML file delete failed.");
            }
            if (xmlFile.createNewFile() == false) {
                // If the XML file cannot be created, throw an exception
                throw (IOException) new IOException("XML file creation failed.");
            } else {
                // Otherwise, everything is good, write XML content into XML file
                toXmlOutputFile =
                        new PrintWriter(new BufferedWriter(new FileWriter(XMLfileName)));
                toXmlOutputFile.println("First arg to Handler is: " + argOne + "\n");
                toXmlOutputFile.println(xml);
                toXmlOutputFile.close();
            }
        } catch (Throwable e) {
            // Exception handling
            e.printStackTrace();
        }
    }
}