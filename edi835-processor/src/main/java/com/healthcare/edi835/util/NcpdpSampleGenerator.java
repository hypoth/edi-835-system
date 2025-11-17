package com.healthcare.edi835.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Utility class for generating synthetic NCPDP D.0 claims for testing.
 *
 * <p>Generates realistic-looking pharmacy claims with various scenarios:</p>
 * <ul>
 *   <li>Brand name prescriptions</li>
 *   <li>Generic refills</li>
 *   <li>Compound medications</li>
 *   <li>Controlled substances</li>
 *   <li>Specialty drugs</li>
 *   <li>Rejected claims</li>
 * </ul>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>
 * NcpdpSampleGenerator generator = new NcpdpSampleGenerator();
 *
 * // Generate 100 random claims
 * List&lt;String&gt; claims = generator.generateClaims(100);
 *
 * // Write to file
 * generator.writeToFile(claims, "d0-samples/test-claims.txt");
 *
 * // Generate specific types
 * String brandClaim = generator.generateBrandNameClaim();
 * String genericClaim = generator.generateGenericClaim();
 * String rejectedClaim = generator.generateRejectedClaim();
 * </pre>
 */
@Slf4j
public class NcpdpSampleGenerator {

    private static final Random RANDOM = new Random();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // Sample data pools
    private static final String[] PAYERS = {
        "EXPRESS-SCRIPTS", "CVS-CAREMARK", "OPTUM-RX", "CIGNA", "HUMANA",
        "AETNA", "BCBS-CA", "ANTHEM", "UNITED-HEALTHCARE", "MEDICAID-CA"
    };

    private static final String[] PHARMACIES = {
        "CVS-001", "WALGREENS-002", "WALMART-003", "RITE-AID-004", "KROGER-005",
        "COSTCO-006", "SAFEWAY-007", "TARGET-008", "PUBLIX-009", "ALBERTSONS-010"
    };

    private static final String[] BINS = {
        "610020", "610084", "003858", "610279", "610378",
        "004336", "610014", "610455", "610591", "011506"
    };

    private static final String[] PCNS = {
        "RX001", "RX002", "CRDHLTH", "ADV", "CHOICE",
        "PRIME", "ADVANCE", "PREFERRED", "STANDARD", "VALUE"
    };

    // NDC codes for various drug types
    private static final String[] BRAND_NDCS = {
        "00002-7510-02", "00069-1530-68", "00173-0687-00", "00186-0122-09",
        "00310-0219-39", "00378-6155-93", "00456-3004-01", "00527-1341-37"
    };

    private static final String[] GENERIC_NDCS = {
        "00093-0058-01", "00172-2275-60", "00228-2063-11", "00378-0781-93",
        "00591-0405-01", "00781-1506-10", "16729-0013-10", "43547-0362-10"
    };

    private static final String[] DRUG_NAMES = {
        "LIPITOR 20MG TAB", "ATORVASTATIN 20MG TAB", "LISINOPRIL 10MG TAB",
        "METFORMIN 500MG TAB", "AMLODIPINE 5MG TAB", "OMEPRAZOLE 20MG CAP",
        "LEVOTHYROXINE 50MCG TAB", "ALBUTEROL HFA 90MCG INH", "GABAPENTIN 300MG CAP",
        "SERTRALINE 50MG TAB", "LOSARTAN 50MG TAB", "ESCITALOPRAM 10MG TAB"
    };

    /**
     * Generates a specified number of random NCPDP claims
     *
     * @param count number of claims to generate
     * @return list of NCPDP transaction strings
     */
    public List<String> generateClaims(int count) {
        List<String> claims = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            // Mix of different claim types
            int type = RANDOM.nextInt(10);
            String claim = switch (type) {
                case 0, 1, 2, 3 -> generateBrandNameClaim();  // 40% brand
                case 4, 5, 6 -> generateGenericClaim();       // 30% generic
                case 7 -> generateCompoundClaim();            // 10% compound
                case 8 -> generateControlledSubstanceClaim(); // 10% controlled
                case 9 -> generateRejectedClaim();            // 10% rejected
                default -> generateBrandNameClaim();
            };
            claims.add(claim);
        }

        log.info("Generated {} NCPDP claims", count);
        return claims;
    }

    /**
     * Generates a brand name prescription claim
     */
    public String generateBrandNameClaim() {
        String txnId = generateTransactionId();
        String payer = randomElement(PAYERS);
        String pharmacy = randomElement(PHARMACIES);
        String bin = randomElement(BINS);
        String pcn = randomElement(PCNS);
        String ndc = randomElement(BRAND_NDCS);
        String drugName = randomElement(DRUG_NAMES);
        String patientId = generatePatientId();
        String rxNumber = generateRxNumber();
        String date = generateDate();

        double ingredientCost = 50.00 + (RANDOM.nextDouble() * 200.00);
        double dispensingFee = 2.50;
        double totalCost = ingredientCost + dispensingFee;
        double patientPay = 10.00 + (RANDOM.nextDouble() * 40.00);
        double planPays = totalCost - patientPay;

        return String.format("""
            STX*D0*%s*
            AM01*01*%s*1234 MAIN ST*ANYTOWN*CA*90210*
            AM04*%s*%s*
            AM07*%s*%s*%s*01*JOHNSON*MARY*L*19900223*F*321 MAIN ST*NEW YORK*NY*10002*
            AM11*1234567890*1*9876543210*SMITH*JOHN*A*555-123-4567*
            AM13*%s*%s*1*%s*LIPITOR*20MG*TAB*30*EA*0*5*1*30*
            AM15*%s*
            AM17*01*%.2f*02*%.2f*03*%.2f*04*%.2f*05*0.00*11*%.2f*
            AN02*APPROVED*A*
            AN23*01*%.2f*02*%.2f*03*%.2f*05*%.2f*
            SE*%s*
            """,
            txnId, pharmacy, bin, pcn, payer, bin, patientId,
            date, rxNumber, ndc,
            ndc,
            ingredientCost, ingredientCost - patientPay, dispensingFee, dispensingFee, ingredientCost,
            ingredientCost - patientPay, dispensingFee, patientPay, planPays,
            txnId);
    }

    /**
     * Generates a generic drug refill claim
     */
    public String generateGenericClaim() {
        String txnId = generateTransactionId();
        String payer = randomElement(PAYERS);
        String pharmacy = randomElement(PHARMACIES);
        String bin = randomElement(BINS);
        String pcn = randomElement(PCNS);
        String ndc = randomElement(GENERIC_NDCS);
        String patientId = generatePatientId();
        String rxNumber = generateRxNumber();
        String date = generateDate();
        int refillNumber = RANDOM.nextInt(5) + 1;

        double ingredientCost = 5.00 + (RANDOM.nextDouble() * 30.00);
        double dispensingFee = 1.75;
        double totalCost = ingredientCost + dispensingFee;
        double patientPay = 5.00;
        double planPays = totalCost - patientPay;

        return String.format("""
            STX*D0*%s*
            AM01*01*%s*5678 OAK AVE*SOMEWHERE*CA*90211*
            AM04*%s*%s*
            AM07*%s*%s*%s*01*SMITH*ROBERT*J*19751115*M*456 OAK AVE*CHICAGO*IL*60601*
            AM11*9876543210*1*1122334455*JONES*MARY*E*555-234-5678*
            AM13*%s*%s*%d*%s*LISINOPRIL*10MG*TAB*90*EA*1*11*1*90*
            AM15*%s*
            AM17*01*%.2f*02*%.2f*03*%.2f*04*%.2f*05*0.00*11*%.2f*
            AN02*APPROVED*A*
            AN23*01*%.2f*02*%.2f*03*%.2f*05*%.2f*
            SE*%s*
            """,
            txnId, pharmacy, bin, pcn, payer, bin, patientId,
            date, rxNumber, refillNumber, ndc,
            ndc,
            ingredientCost, ingredientCost - patientPay, dispensingFee, dispensingFee, ingredientCost,
            ingredientCost - patientPay, dispensingFee, patientPay, planPays,
            txnId);
    }

    /**
     * Generates a compound medication claim
     */
    public String generateCompoundClaim() {
        String txnId = generateTransactionId();
        String payer = randomElement(PAYERS);
        String pharmacy = randomElement(PHARMACIES);
        String bin = randomElement(BINS);
        String pcn = randomElement(PCNS);
        String patientId = generatePatientId();
        String rxNumber = generateRxNumber();
        String date = generateDate();

        double ingredientCost = 120.00 + (RANDOM.nextDouble() * 300.00);
        double dispensingFee = 5.00;
        double compoundFee = 15.00;
        double totalCost = ingredientCost + dispensingFee + compoundFee;
        double patientPay = 25.00 + (RANDOM.nextDouble() * 75.00);
        double planPays = totalCost - patientPay;

        return String.format("""
            STX*D0*%s*
            AM01*01*%s*9012 ELM ST*NOWHERE*CA*90212*
            AM04*%s*%s*
            AM07*%s*%s*%s*01*BROWN*LISA*M*19820408*F*789 ELM ST*BOSTON*MA*02101*
            AM11*1122334455*1*2233445566*BROWN*ROBERT*M*555-345-6789*
            AM13*%s*%s*1*99999999999*COMPOUND-MED*VARIOUS*COMPOUND*60*ML*0*0*1*60*
            AM14*001*00002-7510-02*50.00*002*00069-1530-68*70.00*
            AM15*99999999999*
            AM17*01*%.2f*02*%.2f*03*%.2f*04*%.2f*05*0.00*11*%.2f*
            AN02*APPROVED*A*
            AN23*01*%.2f*02*%.2f*03*%.2f*05*%.2f*
            SE*%s*
            """,
            txnId, pharmacy, bin, pcn, payer, bin, patientId,
            date, rxNumber,
            ingredientCost, ingredientCost - patientPay, dispensingFee + compoundFee, dispensingFee + compoundFee, ingredientCost,
            ingredientCost - patientPay, dispensingFee + compoundFee, patientPay, planPays,
            txnId);
    }

    /**
     * Generates a controlled substance claim (Schedule II-V)
     */
    public String generateControlledSubstanceClaim() {
        String txnId = generateTransactionId();
        String payer = randomElement(PAYERS);
        String pharmacy = randomElement(PHARMACIES);
        String bin = randomElement(BINS);
        String pcn = randomElement(PCNS);
        String patientId = generatePatientId();
        String rxNumber = generateRxNumber();
        String date = generateDate();
        String deaNumber = "AB1234563"; // DEA number format

        double ingredientCost = 40.00 + (RANDOM.nextDouble() * 150.00);
        double dispensingFee = 3.00;
        double totalCost = ingredientCost + dispensingFee;
        double patientPay = 15.00;
        double planPays = totalCost - patientPay;

        return String.format("""
            STX*D0*%s*
            AM01*01*%s*3456 PINE RD*ANYPLACE*CA*90213*
            AM04*%s*%s*
            AM07*%s*%s*%s*01*WILLIAMS*DAVID*K*19650920*M*321 PINE RD*SEATTLE*WA*98101*
            AM11*%s*1*3344556677*WILLIAMS*SARAH*K*555-456-7890*
            AM13*%s*%s*1*00406-0489-03*OXYCODONE*10MG*TAB*30*EA*1*0*1*30*
            AM15*00406-0489-03*
            AM17*01*%.2f*02*%.2f*03*%.2f*04*%.2f*05*0.00*11*%.2f*
            AM19*PA123456*PRIOR-AUTH*
            AN02*APPROVED*A*
            AN23*01*%.2f*02*%.2f*03*%.2f*05*%.2f*
            SE*%s*
            """,
            txnId, pharmacy, bin, pcn, payer, bin, patientId,
            deaNumber,
            date, rxNumber,
            ingredientCost, ingredientCost - patientPay, dispensingFee, dispensingFee, ingredientCost,
            ingredientCost - patientPay, dispensingFee, patientPay, planPays,
            txnId);
    }

    /**
     * Generates a rejected claim
     */
    public String generateRejectedClaim() {
        String txnId = generateTransactionId();
        String payer = randomElement(PAYERS);
        String pharmacy = randomElement(PHARMACIES);
        String bin = randomElement(BINS);
        String pcn = randomElement(PCNS);
        String ndc = randomElement(BRAND_NDCS);
        String patientId = generatePatientId();
        String rxNumber = generateRxNumber();
        String date = generateDate();

        String[] rejectCodes = {"70", "75", "76", "88", "99"};
        String[] rejectMessages = {
            "Product not covered",
            "Prior authorization required",
            "Plan limitations exceeded",
            "Patient not eligible",
            "Non-participating pharmacy"
        };

        int rejectIndex = RANDOM.nextInt(rejectCodes.length);

        double ingredientCost = 50.00 + (RANDOM.nextDouble() * 100.00);
        double dispensingFee = 2.50;
        double totalCost = ingredientCost + dispensingFee;

        return String.format("""
            STX*D0*%s*
            AM01*01*%s*7890 MAPLE LN*SOMEWHERE*CA*90214*
            AM04*%s*%s*
            AM07*%s*%s*%s*01*DAVIS*JENNIFER*A*19881205*F*987 MAPLE LN*PORTLAND*OR*97201*
            AM11*5544332211*1*4455667788*DAVIS*MICHAEL*R*555-567-8901*
            AM13*%s*%s*1*%s*AMOXICILLIN*500MG*CAP*30*EA*0*0*1*10*
            AM15*%s*
            AM17*01*%.2f*02*0.00*03*%.2f*04*0.00*05*0.00*11*%.2f*
            AN02*REJECTED*R*%s*%s*
            AN23*01*0.00*02*0.00*03*%.2f*05*0.00*
            SE*%s*
            """,
            txnId, pharmacy, bin, pcn, payer, bin, patientId,
            date, rxNumber, ndc,
            ndc,
            ingredientCost, dispensingFee, ingredientCost,
            rejectCodes[rejectIndex], rejectMessages[rejectIndex],
            totalCost,
            txnId);
    }

    /**
     * Writes generated claims to a file
     *
     * @param claims list of claim strings
     * @param filePath output file path
     * @throws IOException if file writing fails
     */
    public void writeToFile(List<String> claims, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write("# NCPDP D.0 Sample Claims\n");
            writer.write("# Generated: " + LocalDate.now() + "\n");
            writer.write("# Total claims: " + claims.size() + "\n");
            writer.write("\n");

            for (String claim : claims) {
                writer.write(claim);
                writer.write("\n");
            }
        }

        log.info("Wrote {} claims to {}", claims.size(), filePath);
    }

    // ==================== Helper Methods ====================

    private String generateTransactionId() {
        return "TXN" + System.currentTimeMillis() + "-" + RANDOM.nextInt(1000);
    }

    private String generatePatientId() {
        return "PAT" + String.format("%09d", RANDOM.nextInt(1000000000));
    }

    private String generateRxNumber() {
        return String.format("RX%08d", RANDOM.nextInt(100000000));
    }

    private String generateDate() {
        LocalDate date = LocalDate.now().minusDays(RANDOM.nextInt(90));
        return date.format(DATE_FORMATTER);
    }

    private <T> T randomElement(T[] array) {
        return array[RANDOM.nextInt(array.length)];
    }

    // ==================== Main Method for Standalone Use ====================

    /**
     * Main method for standalone usage
     *
     * <p>Usage:</p>
     * <pre>
     * java NcpdpSampleGenerator [count] [outputFile]
     *
     * Examples:
     *   java NcpdpSampleGenerator 100
     *   java NcpdpSampleGenerator 500 custom-claims.txt
     * </pre>
     */
    public static void main(String[] args) {
        int count = 100;
        String outputFile = "d0-samples/ncpdp_rx_claims.txt";

        if (args.length > 0) {
            try {
                count = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid count: " + args[0]);
                System.exit(1);
            }
        }

        if (args.length > 1) {
            outputFile = args[1];
        }

        System.out.println("Generating " + count + " NCPDP claims...");

        NcpdpSampleGenerator generator = new NcpdpSampleGenerator();
        List<String> claims = generator.generateClaims(count);

        try {
            generator.writeToFile(claims, outputFile);
            System.out.println("✓ Successfully wrote " + count + " claims to " + outputFile);
            System.out.println("\nBreakdown:");
            System.out.println("  ~40% Brand name prescriptions");
            System.out.println("  ~30% Generic refills");
            System.out.println("  ~10% Compound medications");
            System.out.println("  ~10% Controlled substances");
            System.out.println("  ~10% Rejected claims");
            System.out.println("\nNext step: Ingest with ./ingest-ncpdp-claims.sh -f " + outputFile);
        } catch (IOException e) {
            System.err.println("✗ Failed to write file: " + e.getMessage());
            System.exit(1);
        }
    }
}
