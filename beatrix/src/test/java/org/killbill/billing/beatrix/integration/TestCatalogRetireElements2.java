/*
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2014-2024 The Billing Project, LLC
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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.commons.utils.io.Resources;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class TestCatalogRetireElements2 extends TestIntegrationBase {

    private CallContext testCallContext;
    private Account account;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        // Setup tenant
        clock.setTime(new DateTime("2024-01-10T00:00:00"));
        testCallContext = setupTenant();

        // Setup account in right tenant
        account = setupAccount(testCallContext);

        //upload catalog
        uploadCatalog("fixedterm-and-evergreen-v1.xml");
    }


    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/2020")
    public void testRetirePlan() throws Exception {

        //2024-01-10 - create subscription to standard-monthly-fixedterm with startDate=2024-01-20
        PlanPhaseSpecifier spec = new PlanPhaseSpecifier("standard-monthly-fixedterm");
        LocalDate startDate = new LocalDate(2024, 1, 20);
        UUID entitlementId1 = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "fixedterm", startDate, startDate, false, true, Collections.emptyList(), testCallContext);
        assertNotNull(entitlementId1);

        //2024-01-10 - create subscription to standard-monthly-evergreen with startDate=2024-05-20
        spec = new PlanPhaseSpecifier("standard-monthly-evergreen");
        startDate = new LocalDate(2024, 5, 20);
        UUID entitlementId2 = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "evergreen", startDate, startDate, false, true, Collections.emptyList(), testCallContext);
        assertNotNull(entitlementId2);

        //move clock to 2024-04-11 - 3 invoices generated corresponding to standard-monthly-fixedterm for 01/20, 02/20 and 03/20. One month left to bill
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2024,4,11));
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), true, true, true, testCallContext);
        assertEquals(invoices.size(), 3);

        //upload v2 (effectiveDate=2024-04-11)
        uploadCatalog("fixedterm-and-evergreen-v2.xml");

        //move clock to 2024-04-20 - test fails here - no invoice generated. Expected behavior is that an invoice should be generated corresponding to entitlementId1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2024,4,20));
        assertListenerStatus();


    }

    private void uploadCatalog(final String name) throws CatalogApiException, IOException, URISyntaxException {
        final Path path = Paths.get(Resources.getResource("catalogs/testCatalogRetireElements2/" + name).toURI());
        catalogUserApi.uploadCatalog(Files.readString(path), testCallContext);
    }


}
