package vn.phongtroxanh.backend.common.payment;

import org.junit.jupiter.api.Test;
import vn.payos.PayOS;
import vn.phongtroxanh.backend.common.exception.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PayOsSignatureTest {
    @Test void sdkRejectsTamperedWebhookAndAcceptsValidSignature() {
        String key="mvp-test-checksum-key";
        PayOS signing=new PayOS("test-client","test-api",key);
        var adapter=new PayOsPaymentAdapter("test-client","test-api",key);
        try {
            var data=new TreeMap<String,Object>();
            data.put("orderCode",1000000000L); data.put("amount",49000L); data.put("description","PTX123");
            data.put("accountNumber","123"); data.put("reference","ref123"); data.put("transactionDateTime","2026-10-01 11:00:00");
            data.put("currency","VND"); data.put("paymentLinkId","link123"); data.put("code","00"); data.put("desc","success");
            for (String field : List.of("counterAccountBankId","counterAccountBankName","counterAccountName","counterAccountNumber","virtualAccountName","virtualAccountNumber")) data.put(field,"");
            String signature=signing.getCrypto().createSignatureFromObj(data,key);
            var payload=new HashMap<String,Object>(); payload.put("code","00"); payload.put("desc","success");
            payload.put("success",true); payload.put("data",data); payload.put("signature",signature);
            assertEquals(49000L,adapter.verify(payload).getAmount());
            data.put("amount",1L);
            assertThrows(BadRequestException.class,()->adapter.verify(payload));
        } finally { signing.close(); adapter.close(); }
    }
    @Test void missingKeysDisablePaymentsWithoutFakeCheckout() {
        var adapter=new PayOsPaymentAdapter("","","");
        var error=assertThrows(AppException.class,adapter::requireConfigured);
        assertEquals("PAYOS_NOT_CONFIGURED",error.getCode());
    }
}
