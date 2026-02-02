/*
 * Copyright 2020-2026 Equinix, Inc
 * Copyright 2014-2026 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.beatrix.integration;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.commons.utils.io.Resources;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestCatalogWithEffectiveDateForExistingSubscriptions4 extends TestIntegrationBase {

    private CallContext testCallContext;
    private Account account;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        // Setup tenant
        clock.setTime(new DateTime("2025-10-20T10:00:00"));
        testCallContext = setupTenant();

        // Setup account in right tenant
        account = setupAccount(testCallContext);
    }

    @Test(groups = "slow")
    public void testRecurringPlan() throws Exception {

        uploadCatalog("catalog-v1.xml");
        assertListenerStatus();

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("standard-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null, null), UUID.randomUUID().toString(), null, null, false, true, Collections.emptyList(), testCallContext);
        assertListenerStatus();


        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, testCallContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2025, 10, 20), new LocalDate(2025, 11, 20), InvoiceItemType.RECURRING, new BigDecimal("30")));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1); //2025-11-20
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, testCallContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2025, 11, 20), new LocalDate(2025, 12, 20), InvoiceItemType.RECURRING, new BigDecimal("30")));

        clock.setDay(new LocalDate(2025, 12, 4));

        uploadCatalog("catalog-v2.xml");

        LocalDate targetDate = new LocalDate(2025, 12, 20);
        Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), targetDate, new TestDryRunArguments(DryRunType.TARGET_DATE), Collections.emptyList(), testCallContext);
        assertNotNull(dryRunInvoice);
        //The invoice has 5 invoice items, there are REPAIR items generated for previous invoices. Is this correct?
        //My thoughts are that this is the correct behavior:
        //v1 - catalog effective date - 2025-08-08T03:38:03Z
        //v2 - catalog effective date - 2025-09-10T04:18:59Z, subscription effective date -  2025-12-04T03:59:29Z
        //After v2 is uploaded, it becomes the active catalog version (since catalog effective date is before today's date).
        // Hence, all the past invoices are repaired even though effectiveDateForExistingSubscriptions=2025-12-04T03:59:29Z
        assertEquals(dryRunInvoice.getInvoiceItems().size(), 5);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2025, 10, 20), new LocalDate(2025, 11, 20), InvoiceItemType.RECURRING, new BigDecimal("60")),
                new ExpectedInvoiceItemCheck(new LocalDate(2025, 10, 20), new LocalDate(2025, 11, 20), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-30")),
                new ExpectedInvoiceItemCheck(new LocalDate(2025, 11, 20), new LocalDate(2025, 12, 20), InvoiceItemType.RECURRING, new BigDecimal("60")),
                new ExpectedInvoiceItemCheck(new LocalDate(2025, 11, 20), new LocalDate(2025, 12, 20), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-30")),
                new ExpectedInvoiceItemCheck(new LocalDate(2025, 12, 20), new LocalDate(2026, 1, 20),  InvoiceItemType.RECURRING, new BigDecimal("60"))
                                                                  ));
    }

    private void uploadCatalog(final String name) throws CatalogApiException, IOException, URISyntaxException {
        final Path path = Paths.get(Resources.getResource("catalogs/testCatalogEffectiveDateWithExistingSubscriptions4/" + name).toURI());
        catalogUserApi.uploadCatalog(Files.readString(path), testCallContext);
    }


}
