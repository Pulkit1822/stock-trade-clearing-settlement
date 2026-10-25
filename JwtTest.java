import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;

public class JwtTest {
    public static void main(String[] args) throws Exception {
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjoiUEFSVElDSVBBTlQiLCJpYXQiOjE3ODE1NTEyMDAsImV4cCI6MTc4MTU4MDAwMH0.Hxy-aoNdMpe93sLnMKgpgXT_kXzBvZCN2yA0uTU0Rmk";
        String[] parts = token.split("\\.");
        String header = parts[0];
        String payload = parts[1];
        String sig = parts[2];
        
        String secret = "clearing-platform-jwt-secret-key";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expected = mac.doFinal((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        
        String expectedSig = Base64.getUrlEncoder().withoutPadding().encodeToString(expected);
        System.out.println("Expected: " + expectedSig);
        System.out.println("Actual  : " + sig);
    }
}
