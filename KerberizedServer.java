import org.ietf.jgss.*;
import java.io.*;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.Properties;
import java.security.PrivilegedAction;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

public class KerberizedServer  {
  // Oid mechanism = use Kerberos V5 as the security mechanism.
  static Oid krb5Oid;
    
    public static void main(String[] args) 
	throws IOException, GSSException {
      // 1. Set up Kerberos properties.
      // 1.1. Oid mechanism = use Kerberos V5 as the security mechanism.
      krb5Oid = new Oid( "1.2.840.113554.1.2.2");

      // 1.2 Properties
      Properties props = new Properties();
      props.load( new FileInputStream( "server.properties"));
      System.setProperty( "sun.security.krb5.debug", "true");
      System.setProperty( "java.security.auth.login.config", "./jaas.conf");
      System.setProperty( "javax.security.auth.useSubjectCredsOnly", "true");
      String password = props.getProperty( "service.password");

      // 2. Login to the KDC.
      LoginContext loginCtx = null;
      // "KerberizedServer" refers to a section of the JAAS configuration in the jaas.conf file.
      Subject subject = null;
      try {
        loginCtx = new LoginContext( "KerberizedServer",
                                     new LoginCallbackHandler( password));
        loginCtx.login();
        subject = loginCtx.getSubject();
      }
      catch (LoginException e) {
        System.err.println("Login failure : " + e);
        System.exit(-1);
      }
      // Obtain the command-line arguments and parse the port number
      
      if (args.length != 1) {
        System.err.println("Usage: java <options> SampleServer <localPort>");
        System.exit(-1);
      }
      
      int localPort = Integer.parseInt(args[0]);
      ServerSocket ss = new ServerSocket(localPort);
      
      while (true) {
        
        System.out.println("SampleServer::main() Waiting for client connection...");
        
        Socket socket = ss.accept();
        final DataInputStream inStream =
          new DataInputStream(socket.getInputStream());
        final DataOutputStream outStream = 
          new DataOutputStream(socket.getOutputStream());

        System.out.println("SampleServer::main() Got connection from client "
                           + socket.getInetAddress());
        
        // Do the context establishment loop.
        byte[] token = null;
	
        Subject.doAs( subject, new PrivilegedAction<String>() {
            public String run() {
              try {
                // Identify the server that communications are being made to.

                GSSManager manager = GSSManager.getInstance();
                GSSContext context = manager.createContext( (GSSCredential) null);
                
                while (!context.isEstablished()) {
                  System.out.println("SampleServer::main() context not yet established: accepting from client.");
                  
                  context.acceptSecContext(inStream,outStream);
                }
                
                System.out.print("Context Established! ");
                System.out.println("Client is " + context.getSrcName());
                System.out.println("Server is " + context.getTargName());
                return "GOTHERE";
              }
              catch ( Exception e) {
                e.printStackTrace();
                return null;
              }
            }
          }
          );
        
	
        System.out.println("SampleServer::main() Closing connection with client " 
                           + socket.getInetAddress());
        socket.close();
      }
    }
}
