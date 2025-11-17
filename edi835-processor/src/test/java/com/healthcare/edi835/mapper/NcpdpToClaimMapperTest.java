package com.healthcare.edi835.mapper;

import com.healthcare.edi835.model.Claim;
import com.healthcare.edi835.model.ncpdp.*;
import com.healthcare.edi835.ncpdp.parser.NcpdpD0Parser;
import com.healthcare.edi835.ncpdp.parser.NcpdpParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NcpdpToClaimMapper
 */
class NcpdpToClaimMapperTest {

    private NcpdpToClaimMapper mapper;
    private NcpdpD0Parser parser;

    @BeforeEach
    void setUp() {
        mapper = new NcpdpToClaimMapper();
        parser = new NcpdpD0Parser();
    }

    @Test
    void testMapApprovedClaim() throws NcpdpParseException {
        // Sample approved claim
        String rawContent = """
            STX*D0*          *          *
            AM01*1234567*PHARMACY001*20241014*143025*1*
            AM04*01*R*1*
            AM07*BCBSIL*60054*123456789*01*SMITH*JOHN*A*19850515*M*456 PATIENT AVE*CHICAGO*IL*60602*
            AM11*00123456789*1*1234567890*JONES*ROBERT*D*555-123-4567*
            AM13*20241014*12345*1*00002012345678*LIPITOR*20MG*TAB*30*EA*1*0*0*3*
            AM15*59762-0123-03*
            AM17*01*250.00*02*225.00*03*20.00*04*5.00*05*0.00*11*250.00*
            AMC1*123456789012345*
            SE*15*1234567*
            AN02*A*00*APPROVED*
            AN23*01*225.00*02*5.00*03*20.00*05*230.00*
            AN25*CLAIM APPROVED*AUTH123456*
            """;

        NcpdpTransaction ncpdpTx = parser.parse(rawContent);
        Claim claim = mapper.mapToClaim(ncpdpTx);

        // Verify basic fields
        assertNotNull(claim);
        assertNotNull(claim.getId());
        assertTrue(claim.getId().startsWith("NCPDP-PHARMACY001-12345"));

        // Verify payer/payee
        assertEquals("BCBSIL", claim.getPayerId());
        assertEquals("PHARMACY001", claim.getPayeeId());

        // Verify claim number
        assertEquals("12345", claim.getClaimNumber());

        // Verify patient info
        assertEquals("123456789", claim.getPatientId());
        assertEquals("JOHN A SMITH", claim.getPatientName());
        assertEquals("60054", claim.getBinNumber());

        // Verify dates
        assertEquals(LocalDate.of(2024, 10, 14), claim.getServiceDate());
        assertEquals(LocalDate.of(2024, 10, 14), claim.getStatementFromDate());
        assertEquals(LocalDate.of(2024, 10, 14), claim.getStatementToDate());

        // Verify financial amounts
        assertEquals(new BigDecimal("250.00"), claim.getTotalChargeAmount());
        assertEquals(new BigDecimal("230.00"), claim.getPaidAmount());
        assertEquals(new BigDecimal("20.00"), claim.getPatientResponsibilityAmount());

        // Verify status
        assertEquals(Claim.ClaimStatus.PAID, claim.getStatus());
        assertEquals("APPROVED", claim.getStatusReason());

        // Verify service lines
        assertNotNull(claim.getServiceLines());
        assertEquals(1, claim.getServiceLines().size());

        Claim.ServiceLine serviceLine = claim.getServiceLines().get(0);
        assertEquals("59762-0123-03", serviceLine.getProcedureCode());
        assertEquals("TAB", serviceLine.getModifier());
        assertEquals(new BigDecimal("250.00"), serviceLine.getChargedAmount());
        assertEquals(new BigDecimal("230.00"), serviceLine.getPaidAmount());
        assertEquals(30, serviceLine.getUnits());
        assertEquals(LocalDate.of(2024, 10, 14), serviceLine.getServiceDate());

        // Verify metadata
        assertNotNull(claim.getProcessedDate());
        assertEquals("NCPDP_D0_MAPPER", claim.getProcessedBy());
    }

    @Test
    void testMapRejectedClaim() throws NcpdpParseException {
        String rawContent = """
            STX*D0*          *          *
            AM01*1234567*PHARMACY001*20241014*143025*1*
            AM04*01*R*1*
            AM07*BCBSIL*60054*123456789*01*SMITH*JOHN*A*19850515*M*456 PATIENT AVE*CHICAGO*IL*60602*
            AM11*00123456789*1*1234567890*JONES*ROBERT*D*555-123-4567*
            AM13*20241014*12345*1*00002012345678*LIPITOR*20MG*TAB*30*EA*1*0*0*3*
            AM17*01*250.00*03*20.00*11*270.00*
            SE*10*1234567*
            AN02*R*99*REJECTED - INVALID BIN*
            """;

        NcpdpTransaction ncpdpTx = parser.parse(rawContent);
        Claim claim = mapper.mapToClaim(ncpdpTx);

        // Verify rejected status
        assertEquals(Claim.ClaimStatus.DENIED, claim.getStatus());
        assertEquals("REJECTED - INVALID BIN", claim.getStatusReason());

        // Verify zero paid amount
        assertEquals(BigDecimal.ZERO, claim.getPaidAmount());

        // Verify patient responsibility equals total charge
        assertEquals(claim.getTotalChargeAmount(), claim.getPatientResponsibilityAmount());

        // Verify adjustments created for denial
        assertNotNull(claim.getAdjustments());
        assertFalse(claim.getAdjustments().isEmpty());

        Claim.ClaimAdjustment adjustment = claim.getAdjustments().get(0);
        assertEquals("PR", adjustment.getGroupCode()); // Patient Responsibility
        assertEquals("REJECTED", adjustment.getReasonCode());
    }

    @Test
    void testMapPendingClaimWithoutResponse() throws NcpdpParseException {
        String rawContent = """
            STX*D0*          *          *
            AM01*2345678*PHARMACY002*20241014*150030*1*
            AM04*01*R*1*
            AM07*CIGNA*62308*987654321*01*JOHNSON*MARY*L*19900223*F*321 MAIN ST*NEW YORK*NY*10002*
            AM11*00987654321*1*9876543210*WILLIAMS*SARAH*M*555-987-6543*
            AM13*20241014*67890*1*00002098765432*LISINOPRIL*10MG*TAB*90*EA*1*1*0*3*
            AM17*01*45.00*02*35.00*03*8.00*04*2.00*11*45.00*
            SE*10*2345678*
            """;

        NcpdpTransaction ncpdpTx = parser.parse(rawContent);
        Claim claim = mapper.mapToClaim(ncpdpTx);

        // Verify processed status (no response)
        assertEquals(Claim.ClaimStatus.PROCESSED, claim.getStatus());

        // Verify amounts use pricing from request
        assertEquals(new BigDecimal("45.00"), claim.getTotalChargeAmount());
    }

    @Test
    void testMapCompoundPrescription() throws NcpdpParseException {
        String rawContent = """
            STX*D0*          *          *
            AM01*3456789*PHARMACY003*20241014*163045*1*
            AM04*01*C*1*
            AM07*AETNA*60055*456789123*01*DAVIS*ROBERT*M*19750810*M*888 OCEAN DR*LOS ANGELES*CA*90002*
            AM11*00456789123*1*4567890123*ANDERSON*LISA*K*555-456-7890*
            AM13*20241014*98765*1*99999999999999*COMPOUND PREP*********3*
            AM14*01*00006020001234*50*ML*125.00*02*00008820004567*25*GM*85.00*03*BASE001*100*GM*40.00*
            AM17*01*250.00*02*200.00*03*40.00*04*10.00*11*250.00*
            SE*12*3456789*
            """;

        NcpdpTransaction ncpdpTx = parser.parse(rawContent);
        Claim claim = mapper.mapToClaim(ncpdpTx);

        // Verify payer
        assertEquals("AETNA", claim.getPayerId());

        // Verify compound mapped as single service line
        assertEquals(1, claim.getServiceLines().size());

        // Verify NDC code handling (compound uses special NDC)
        Claim.ServiceLine serviceLine = claim.getServiceLines().get(0);
        assertEquals("99999999999999", serviceLine.getProcedureCode());
    }

    @Test
    void testMapHighValueSpecialtyDrug() throws NcpdpParseException {
        String rawContent = """
            STX*D0*          *          *
            AM01*6789012*PHARMACY006*20241014*193100*1*
            AM04*01*R*1*
            AM07*ANTHEM*50905*654987321*01*THOMPSON*PATRICIA*A*19720920*F*555 SPECIALTY ST*PHOENIX*AZ*85001*
            AM11*00654987321*1*6549873210*WHITE*DAVID*M*555-654-9873*
            AM13*20241014*77889*1*00005123456789*HUMIRA PEN*40MG/0.8ML*INJ*2*EA*1*0*0*5*
            AM15*00074-4339-02*
            AM17*01*6500.00*02*5200.00*03*1200.00*04*100.00*11*6500.00*
            SE*12*6789012*
            AN02*A*00*APPROVED*
            AN23*01*5200.00*02*100.00*03*1200.00*05*5300.00*
            """;

        NcpdpTransaction ncpdpTx = parser.parse(rawContent);
        Claim claim = mapper.mapToClaim(ncpdpTx);

        // Verify high-value amounts
        assertEquals(new BigDecimal("6500.00"), claim.getTotalChargeAmount());
        assertEquals(new BigDecimal("5300.00"), claim.getPaidAmount());
        assertEquals(new BigDecimal("1200.00"), claim.getPatientResponsibilityAmount());

        // Verify adjustment amount
        BigDecimal expectedAdjustment = new BigDecimal("6500.00")
            .subtract(new BigDecimal("5300.00"))
            .subtract(new BigDecimal("1200.00"));
        assertEquals(BigDecimal.ZERO, claim.getAdjustmentAmount());
    }

    @Test
    void testMapRefillPrescription() throws NcpdpParseException {
        String rawContent = """
            STX*D0*          *          *
            AM01*2345678*PHARMACY002*20241014*150030*1*
            AM04*01*R*2*
            AM07*CIGNA*62308*987654321*01*JOHNSON*MARY*L*19900223*F*321 MAIN ST*NEW YORK*NY*10002*
            AM11*00987654321*1*9876543210*WILLIAMS*SARAH*M*555-987-6543*
            AM13*20241014*67890*2*00002098765432*LISINOPRIL*10MG*TAB*90*EA*2*1*0*3*
            AM17*01*45.00*03*8.00*11*45.00*
            SE*10*2345678*
            """;

        NcpdpTransaction ncpdpTx = parser.parse(rawContent);
        Claim claim = mapper.mapToClaim(ncpdpTx);

        // Verify claim is mapped correctly (fill number doesn't affect mapping)
        assertEquals("CIGNA", claim.getPayerId());
        assertEquals("67890", claim.getClaimNumber());
    }

    @Test
    void testMapMultipleTransactions() throws NcpdpParseException {
        String rawContent1 = """
            STX*D0*          *          *
            AM01*123*PHARM*20241014*120000*1*
            AM04*01*R*1*
            AM07*BCBSIL*12345*111*01*DOE*JOHN**20000101*M*123 ST*CITY*ST*12345*
            AM11*123456789*1*987654321*DR*TEST**555-1234*
            AM13*20241014*001*1*12345678901*DRUG1*10MG*TAB*30*EA*1*0*0*30*
            AM17*01*100.00*11*100.00*
            SE*8*123*
            """;

        String rawContent2 = """
            STX*D0*          *          *
            AM01*456*PHARM*20241014*120000*1*
            AM04*01*R*1*
            AM07*CIGNA*12345*222*01*SMITH*JANE**20000101*F*456 ST*CITY*ST*12345*
            AM11*123456789*1*987654321*DR*TEST**555-1234*
            AM13*20241014*002*1*98765432109*DRUG2*20MG*TAB*60*EA*1*0*0*60*
            AM17*01*200.00*11*200.00*
            SE*8*456*
            """;

        NcpdpTransaction tx1 = parser.parse(rawContent1);
        NcpdpTransaction tx2 = parser.parse(rawContent2);

        List<Claim> claims = mapper.mapToClaims(List.of(tx1, tx2));

        assertEquals(2, claims.size());

        // Verify first claim
        assertEquals("BCBSIL", claims.get(0).getPayerId());
        assertEquals("001", claims.get(0).getClaimNumber());

        // Verify second claim
        assertEquals("CIGNA", claims.get(1).getPayerId());
        assertEquals("002", claims.get(1).getClaimNumber());
    }

    @Test
    void testMapWithMissingOptionalFields() throws NcpdpParseException {
        String rawContent = """
            STX*D0*          *          *
            AM01*123*PHARM*20241014*120000*1*
            AM04*01*R*1*
            AM07*TEST*****DOE*JOHN**20000101*M*******
            AM11*123456789*1*987654321*DR*TEST**555-1234*
            AM13*20241014*123*1*12345678901*DRUG*10MG*TAB*30*EA*1*0*0*30*
            AM17*01*100.00*11*100.00*
            SE*8*123*
            """;

        NcpdpTransaction ncpdpTx = parser.parse(rawContent);
        Claim claim = mapper.mapToClaim(ncpdpTx);

        // Should handle missing fields gracefully
        assertNotNull(claim);
        assertEquals("TEST", claim.getPayerId());
        assertNull(claim.getBinNumber()); // BIN was empty
        assertNotNull(claim.getPatientName()); // Should still build name from available parts
    }

    @Test
    void testMapNullTransaction() {
        assertThrows(IllegalArgumentException.class, () -> {
            mapper.mapToClaim(null);
        });
    }

    @Test
    void testMapTransactionMissingRequiredFields() {
        NcpdpTransaction tx = NcpdpTransaction.builder()
            .version("D0")
            .build();

        assertThrows(IllegalArgumentException.class, () -> {
            mapper.mapToClaim(tx);
        });
    }

    @Test
    void testDateParsingWithInvalidDate() {
        NcpdpTransaction tx = NcpdpTransaction.builder()
            .version("D0")
            .header(TransactionHeader.builder()
                .pharmacyId("PHARM001")
                .date("20241014")
                .build())
            .patient(PatientSegment.builder()
                .carrierId("TEST")
                .cardholderIdNumber("123")
                .build())
            .claim(ClaimSegment.builder()
                .dateOfService("INVALID")
                .prescriptionNumber("123")
                .build())
            .pricing(PricingSegment.builder()
                .grossAmountDue(new BigDecimal("100.00"))
                .build())
            .build();

        // Should use current date as fallback
        Claim claim = mapper.mapToClaim(tx);
        assertNotNull(claim.getServiceDate());
        assertEquals(LocalDate.now(), claim.getServiceDate());
    }

    @Test
    void testAdjustmentCalculation() throws NcpdpParseException {
        String rawContent = """
            STX*D0*          *          *
            AM01*123*PHARM*20241014*120000*1*
            AM04*01*R*1*
            AM07*TEST*12345*999*01*DOE*JOHN**20000101*M*123 ST*CITY*ST*12345*
            AM11*123456789*1*987654321*DR*TEST**555-1234*
            AM13*20241014*123*1*12345678901*DRUG*10MG*TAB*30*EA*1*0*0*30*
            AM17*01*150.00*11*150.00*
            SE*8*123*
            AN02*A*00*APPROVED*
            AN23*01*100.00*03*30.00*05*100.00*
            """;

        NcpdpTransaction ncpdpTx = parser.parse(rawContent);
        Claim claim = mapper.mapToClaim(ncpdpTx);

        // Total: 150, Paid: 100, Patient: 30, Adjustment should be: 150 - 100 - 30 = 20
        assertEquals(new BigDecimal("20.00"), claim.getAdjustmentAmount());

        // Should have contractual adjustment
        assertFalse(claim.getAdjustments().isEmpty());
        Claim.ClaimAdjustment adjustment = claim.getAdjustments().stream()
            .filter(a -> "CO".equals(a.getGroupCode()))
            .findFirst()
            .orElse(null);

        assertNotNull(adjustment);
        assertEquals("45", adjustment.getReasonCode());
    }
}
