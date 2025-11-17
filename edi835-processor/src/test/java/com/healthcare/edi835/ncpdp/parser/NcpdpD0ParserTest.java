package com.healthcare.edi835.ncpdp.parser;

import com.healthcare.edi835.model.ncpdp.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NcpdpD0Parser
 */
class NcpdpD0ParserTest {

    private NcpdpD0Parser parser;

    @BeforeEach
    void setUp() {
        parser = new NcpdpD0Parser();
    }

    @Test
    void testParseBrandNamePrescription() throws NcpdpParseException {
        // Sample 1: Brand Name Prescription - New Rx (Lipitor 20mg)
        String rawContent = """
            STX*D0*          *          *
            AM01*1234567*PHARMACY001*20241014*143025*1*
            AM04*01*R*1*
            AM07*BCBSIL*60054*123456789*01*SMITH*JOHN*A*19850515*M*456 PATIENT AVE*CHICAGO*IL*60602*
            AM11*00123456789*1*1234567890*JONES*ROBERT*D*555-123-4567*
            AM13*20241014*12345*1*00002012345678*LIPITOR*20MG*TAB*30*EA*1*0*0*3*
            AM15*59762-0123-03*
            AM17*01*250.00*02*225.00*03*20.00*04*5.00*05*0.00*06*0.00*07*0.00*11*250.00*
            AM20*01*New therapy*
            AMC1*123456789012345*
            SE*15*1234567*
            """;

        NcpdpTransaction tx = parser.parse(rawContent);

        // Verify transaction basics
        assertNotNull(tx);
        assertEquals("D0", tx.getVersion());

        // Verify header (AM01)
        assertNotNull(tx.getHeader());
        assertEquals("1234567", tx.getHeader().getServiceProviderId());
        assertEquals("PHARMACY001", tx.getHeader().getPharmacyId());
        assertEquals("20241014", tx.getHeader().getDate());
        assertEquals("143025", tx.getHeader().getTime());

        // Verify insurance (AM04)
        assertNotNull(tx.getInsurance());
        assertEquals("01", tx.getInsurance().getCardholderIdQualifier());
        assertEquals("R", tx.getInsurance().getPrescriptionOriginCode());
        assertEquals(1, tx.getInsurance().getFillNumber());

        // Verify patient (AM07)
        assertNotNull(tx.getPatient());
        assertEquals("BCBSIL", tx.getPatient().getCarrierId());
        assertEquals("60054", tx.getPatient().getBinNumber());
        assertEquals("123456789", tx.getPatient().getCardholderIdNumber());
        assertEquals("SMITH", tx.getPatient().getLastName());
        assertEquals("JOHN", tx.getPatient().getFirstName());
        assertEquals("A", tx.getPatient().getMiddleInitial());
        assertEquals("19850515", tx.getPatient().getDateOfBirth());
        assertEquals("M", tx.getPatient().getGender());
        assertEquals("456 PATIENT AVE", tx.getPatient().getAddress());
        assertEquals("CHICAGO", tx.getPatient().getCity());
        assertEquals("IL", tx.getPatient().getState());
        assertEquals("60602", tx.getPatient().getZip());

        // Verify prescriber (AM11)
        assertNotNull(tx.getPrescriber());
        assertEquals("00123456789", tx.getPrescriber().getPrescriberId());
        assertEquals("JONES", tx.getPrescriber().getLastName());
        assertEquals("ROBERT", tx.getPrescriber().getFirstName());

        // Verify claim (AM13)
        assertNotNull(tx.getClaim());
        assertEquals("20241014", tx.getClaim().getDateOfService());
        assertEquals("12345", tx.getClaim().getPrescriptionNumber());
        assertEquals("00002012345678", tx.getClaim().getNdc());
        assertEquals("LIPITOR", tx.getClaim().getProductDescription());
        assertEquals("20MG", tx.getClaim().getStrength());
        assertEquals("TAB", tx.getClaim().getDosageForm());
        assertEquals(new BigDecimal("30"), tx.getClaim().getQuantityDispensed());
        assertEquals("EA", tx.getClaim().getQuantityUnit());
        assertEquals(3, tx.getClaim().getDaysSupply());

        // Verify NDC (AM15)
        assertEquals("59762-0123-03", tx.getNdcCode());

        // Verify pricing (AM17)
        assertNotNull(tx.getPricing());
        assertEquals(new BigDecimal("250.00"), tx.getPricing().getIngredientCostSubmitted());
        assertEquals(new BigDecimal("225.00"), tx.getPricing().getIngredientCostPaid());
        assertEquals(new BigDecimal("20.00"), tx.getPricing().getDispensingFeeSubmitted());
        assertEquals(new BigDecimal("5.00"), tx.getPricing().getDispensingFeePaid());
        assertEquals(new BigDecimal("0.00"), tx.getPricing().getTaxAmount());
        assertEquals(new BigDecimal("250.00"), tx.getPricing().getGrossAmountDue());

        // Verify clinical (AM20)
        assertNotNull(tx.getClinical());
        assertEquals("01", tx.getClinical().getDiagnosisCodeQualifier());
        assertEquals("New therapy", tx.getClinical().getClinicalInformation());

        // Verify claim trailer
        assertEquals("123456789012345", tx.getClaimTrailer());
    }

    @Test
    void testParseGenericPrescriptionWithResponse() throws NcpdpParseException {
        // Sample 2: Generic Prescription - Refill (Lisinopril 10mg) with Response
        String rawContent = """
            STX*D0*          *          *
            AM01*2345678*PHARMACY002*20241014*150030*1*
            AM04*01*R*2*
            AM07*CIGNA*62308*987654321*01*JOHNSON*MARY*L*19900223*F*321 MAIN ST*NEW YORK*NY*10002*
            AM11*00987654321*1*9876543210*WILLIAMS*SARAH*M*555-987-6543*
            AM13*20241014*67890*2*00002098765432*LISINOPRIL*10MG*TAB*90*EA*1*1*0*3*
            AM15*68180-0516-09*
            AM17*01*45.00*02*35.00*03*8.00*04*2.00*05*0.00*06*0.00*07*0.00*11*45.00*
            AM19*20*20240714*
            AMC1*987654321098765*
            SE*15*2345678*
            AN02*A*00*APPROVED*
            AN23*01*35.00*02*8.00*03*2.00*05*45.00*
            AN25*CLAIM APPROVED*REF987654*
            """;

        NcpdpTransaction tx = parser.parse(rawContent);

        // Verify this is a refill
        assertEquals(2, tx.getInsurance().getFillNumber());
        assertTrue(tx.getClaim().isRefill());
        assertFalse(tx.getClaim().isNewPrescription());

        // Verify patient
        assertEquals("CIGNA", tx.getPatient().getCarrierId());
        assertEquals("JOHNSON", tx.getPatient().getLastName());
        assertEquals("MARY", tx.getPatient().getFirstName());
        assertEquals("F", tx.getPatient().getGender());

        // Verify prior authorization
        assertNotNull(tx.getPriorAuthorization());
        assertEquals("20", tx.getPriorAuthorization().getAuthorizationType());
        assertEquals("20240714", tx.getPriorAuthorization().getPriorPrescriptionDate());

        // Verify response status (AN02)
        assertTrue(tx.hasResponse());
        assertTrue(tx.isApproved());
        assertFalse(tx.isRejected());
        assertNotNull(tx.getResponseStatus());
        assertEquals("A", tx.getResponseStatus().getResponseStatus());
        assertEquals("00", tx.getResponseStatus().getResponseCode());
        assertEquals("APPROVED", tx.getResponseStatus().getResponseMessage());

        // Verify response payment (AN23)
        assertNotNull(tx.getResponsePayment());
        assertEquals(new BigDecimal("35.00"), tx.getResponsePayment().getIngredientCostPaid());
        assertEquals(new BigDecimal("8.00"), tx.getResponsePayment().getDispensingFeePaid());
        assertEquals(new BigDecimal("2.00"), tx.getResponsePayment().getPatientPayAmount());
        assertEquals(new BigDecimal("45.00"), tx.getResponsePayment().getTotalAmountPaid());

        // Verify response message (AN25)
        assertNotNull(tx.getResponseMessage());
        assertEquals("CLAIM APPROVED", tx.getResponseMessage().getMessageText());
        assertEquals("REF987654", tx.getResponseMessage().getAuthorizationNumber());
    }

    @Test
    void testParseCompoundPrescription() throws NcpdpParseException {
        // Sample 3: Compound Prescription - Topical Cream
        String rawContent = """
            STX*D0*          *          *
            AM01*3456789*PHARMACY003*20241014*163045*1*
            AM04*01*C*1*
            AM07*AETNA*60055*456789123*01*DAVIS*ROBERT*M*19750810*M*888 OCEAN DR*LOS ANGELES*CA*90002*
            AM11*00456789123*1*4567890123*ANDERSON*LISA*K*555-456-7890*
            AM13*20241014*98765*1*99999999999999*COMPOUND PREP*********3*
            AM14*01*00006020001234*50*ML*125.00*02*00008820004567*25*GM*85.00*03*BASE001*100*GM*40.00*
            AM17*01*250.00*02*200.00*03*40.00*04*10.00*05*0.00*06*0.00*07*0.00*11*250.00*
            AM20*03*Custom compounded cream per physician order*
            AMC1*456789123456789*
            SE*17*3456789*
            """;

        NcpdpTransaction tx = parser.parse(rawContent);

        // Verify compound prescription
        assertTrue(tx.isCompound());
        assertEquals("C", tx.getInsurance().getPrescriptionOriginCode());

        // Verify compound segment (AM14)
        assertNotNull(tx.getCompound());
        assertNotNull(tx.getCompound().getIngredients());
        assertEquals(3, tx.getCompound().getIngredients().size());

        // Verify first ingredient
        CompoundSegment.CompoundIngredient ingredient1 = tx.getCompound().getIngredients().get(0);
        assertEquals(1, ingredient1.getSequenceNumber());
        assertEquals("00006020001234", ingredient1.getProductCode());
        assertEquals("50", ingredient1.getQuantity());
        assertEquals("ML", ingredient1.getQuantityUnit());
        assertEquals("125.00", ingredient1.getCost());

        // Verify second ingredient
        CompoundSegment.CompoundIngredient ingredient2 = tx.getCompound().getIngredients().get(1);
        assertEquals(2, ingredient2.getSequenceNumber());
        assertEquals("00008820004567", ingredient2.getProductCode());
        assertEquals("25", ingredient2.getQuantity());
        assertEquals("GM", ingredient2.getQuantityUnit());

        // Verify third ingredient
        CompoundSegment.CompoundIngredient ingredient3 = tx.getCompound().getIngredients().get(2);
        assertEquals(3, ingredient3.getSequenceNumber());
        assertEquals("BASE001", ingredient3.getProductCode());
    }

    @Test
    void testParseControlledSubstance() throws NcpdpParseException {
        // Sample 4: Controlled Substance - Hydrocodone (C-II)
        String rawContent = """
            STX*D0*          *          *
            AM01*4567890*PHARMACY004*20241014*173050*1*
            AM04*01*R*1*
            AM07*UNITEDHC*87726*789123456*01*WILSON*JENNIFER*K*19881205*F*999 PARK AVE*HOUSTON*TX*77001*
            AM11*00789123456*1*7891234567*BROWN*MICHAEL*J*555-789-1234*
            AM13*20241014*11223*1*00006012345678*HYDROCODONE-APAP*5-325MG*TAB*20*EA*1*0*0*2*
            AM15*00406-0512-01*
            AM17*01*85.00*02*65.00*03*15.00*04*5.00*05*0.00*06*0.00*07*0.00*11*85.00*
            AM20*01*Post-surgical pain management*
            AM21*01*20241014*STATE123456*TX*1234567*
            AMC1*789123456789123*
            SE*17*4567890*
            """;

        NcpdpTransaction tx = parser.parse(rawContent);

        // Verify patient
        assertEquals("UNITEDHC", tx.getPatient().getCarrierId());
        assertEquals("WILSON", tx.getPatient().getLastName());

        // Verify drug
        assertEquals("HYDROCODONE-APAP", tx.getClaim().getProductDescription());
        assertEquals("5-325MG", tx.getClaim().getStrength());
        assertEquals(2, tx.getClaim().getDaysSupply());

        // Verify additional documentation (AM21)
        assertNotNull(tx.getAdditionalDocumentation());
        assertEquals("01", tx.getAdditionalDocumentation().getDocumentationType());
        assertEquals("STATE123456", tx.getAdditionalDocumentation().getDeaNumber());
        assertEquals("TX", tx.getAdditionalDocumentation().getState());
        assertEquals("1234567", tx.getAdditionalDocumentation().getStateLicenseNumber());
    }

    @Test
    void testParsePriorAuthorizationRequired() throws NcpdpParseException {
        // Sample 6: Prior Authorization Required - Specialty Drug
        String rawContent = """
            STX*D0*          *          *
            AM01*6789012*PHARMACY006*20241014*193100*1*
            AM04*01*R*1*
            AM07*ANTHEM*50905*654987321*01*THOMPSON*PATRICIA*A*19720920*F*555 SPECIALTY ST*PHOENIX*AZ*85001*
            AM11*00654987321*1*6549873210*WHITE*DAVID*M*555-654-9873*
            AM13*20241014*77889*1*00005123456789*HUMIRA PEN*40MG/0.8ML*INJ*2*EA*1*0*0*5*
            AM15*00074-4339-02*
            AM17*01*6500.00*02*5200.00*03*1200.00*04*100.00*05*0.00*06*0.00*07*0.00*11*6500.00*
            AM20*01*Rheumatoid arthritis treatment*
            AM21*03*PA123456789*
            AMC1*654987321654987*
            SE*17*6789012*
            """;

        NcpdpTransaction tx = parser.parse(rawContent);

        // Verify specialty drug
        assertEquals("HUMIRA PEN", tx.getClaim().getProductDescription());
        assertEquals("INJ", tx.getClaim().getDosageForm());
        assertEquals(new BigDecimal("6500.00"), tx.getPricing().getGrossAmountDue());

        // Verify prior authorization documentation
        assertNotNull(tx.getAdditionalDocumentation());
        assertEquals("03", tx.getAdditionalDocumentation().getDocumentationType());
        assertEquals("PA123456789", tx.getAdditionalDocumentation().getPriorAuthorizationNumber());
    }

    @Test
    void testParseEmptyContent() {
        assertThrows(NcpdpParseException.class, () -> {
            parser.parse("");
        });
    }

    @Test
    void testParseNullContent() {
        assertThrows(NcpdpParseException.class, () -> {
            parser.parse(null);
        });
    }

    @Test
    void testParseInvalidSegment() {
        String rawContent = """
            STX*D0*          *          *
            AM01*incomplete
            SE*2*123*
            """;

        assertThrows(NcpdpParseException.class, () -> {
            parser.parse(rawContent);
        });
    }

    @Test
    void testPricingSegmentTotals() throws NcpdpParseException {
        String rawContent = """
            STX*D0*          *          *
            AM01*123*PHARM*20241014*120000*1*
            AM04*01*R*1*
            AM07*TEST*12345*999*01*DOE*JOHN**20000101*M*123 ST*CITY*ST*12345*
            AM11*123456789*1*987654321*DR*TEST**555-1234*
            AM13*20241014*123*1*12345678901*DRUG*10MG*TAB*30*EA*1*0*0*30*
            AM17*01*100.00*02*80.00*03*15.00*04*10.00*05*5.00*11*120.00*
            SE*8*123*
            """;

        NcpdpTransaction tx = parser.parse(rawContent);

        assertNotNull(tx.getPricing());
        assertEquals(new BigDecimal("120.00"), tx.getPricing().getTotalSubmitted());
        assertEquals(new BigDecimal("90.00"), tx.getPricing().getTotalPaid());
    }

    @Test
    void testPatientFullName() throws NcpdpParseException {
        String rawContent = """
            STX*D0*          *          *
            AM01*123*PHARM*20241014*120000*1*
            AM04*01*R*1*
            AM07*TEST*12345*999*01*SMITH*JANE*M*20000101*F*123 ST*CITY*ST*12345*
            AM11*123456789*1*987654321*DR*TEST**555-1234*
            AM13*20241014*123*1*12345678901*DRUG*10MG*TAB*30*EA*1*0*0*30*
            SE*5*123*
            """;

        NcpdpTransaction tx = parser.parse(rawContent);

        assertEquals("JANE M SMITH", tx.getPatient().getFullName());
    }
}
