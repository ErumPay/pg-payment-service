package com.erumpay.pgpayment;

import com.erumpay.pgpayment.repository.PgPaymentLedgerRepository;
import com.erumpay.pgpayment.repository.PgPaymentGroupRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
class PgpaymentApplicationTests {

	@MockitoBean
	private PgPaymentLedgerRepository pgPaymentLedgerRepository;

	@MockitoBean
	private PgPaymentGroupRepository pgPaymentGroupRepository;

	@Test
	void contextLoads() {
	}

}
