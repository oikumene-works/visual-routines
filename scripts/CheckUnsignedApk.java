import com.android.apksig.ApkVerifier;

import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Fails unless apksig recognizes only an explicit unsigned app-signature state. */
public final class CheckUnsignedApk {
    private static final Set<ApkVerifier.Issue> ALLOWED_UNSIGNED_ERRORS = EnumSet.of(
            ApkVerifier.Issue.JAR_SIG_NO_MANIFEST,
            ApkVerifier.Issue.JAR_SIG_NO_SIGNATURES
    );

    private CheckUnsignedApk() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            fail("Expected exactly one APK path.");
        }

        ApkVerifier.Result result = new ApkVerifier.Builder(new File(args[0])).build().verify();
        if (result.isVerified()) {
            fail("APK has a valid app signature.");
        }

        boolean hasRecognizedSigner =
                !result.getV1SchemeSigners().isEmpty()
                        || !result.getV1SchemeIgnoredSigners().isEmpty()
                        || !result.getV2SchemeSigners().isEmpty()
                        || !result.getV3SchemeSigners().isEmpty()
                        || !result.getV31SchemeSigners().isEmpty();
        List<ApkVerifier.IssueWithParams> errors = result.getAllErrors();
        boolean hasOnlyExplicitUnsignedErrors =
                !errors.isEmpty()
                        && errors.stream()
                        .allMatch(error -> ALLOWED_UNSIGNED_ERRORS.contains(error.getIssue()));

        if (hasRecognizedSigner || !hasOnlyExplicitUnsignedErrors) {
            for (ApkVerifier.IssueWithParams error : errors) {
                System.err.println("apksig: " + error);
            }
            fail("APK is not in the expected verifier-recognized unsigned state.");
        }
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
