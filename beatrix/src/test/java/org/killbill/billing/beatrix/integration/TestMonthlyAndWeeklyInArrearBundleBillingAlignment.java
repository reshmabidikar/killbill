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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestMonthlyAndWeeklyInArrearBundleBillingAlignment extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testMonthlyAndWeeklyInArrearWithBundleAlignment");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test
    public void testWeeklyInArrearBundleBillingAlignment() throws Exception {
        final LocalDate initialDate = new LocalDate(2024, 5, 15);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));

        //create base subscription with two addons
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("standard-weekly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, Collections.emptyList(), callContext);
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, false, callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final PlanPhaseSpecifier addonSpec1 = new PlanPhaseSpecifier("standard-ao-weekly");
        final UUID addonEntId1 = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addonSpec1), null, null, false, Collections.emptyList(), callContext);
        final Entitlement addonEnt1 = entitlementApi.getEntitlementForId(addonEntId1, false, callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final PlanPhaseSpecifier addonSpec2 = new PlanPhaseSpecifier("standard-ao2-weekly");
        final UUID addonEntId2 = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addonSpec2), null, null, false, Collections.emptyList(), callContext);
        final Entitlement addonEnt2 = entitlementApi.getEntitlementForId(addonEntId2, false, callContext);
        assertListenerStatus();

        // move clock to 2024-05-19 and change standard-ao2-weekly to standard-ao2-weekly-plan2
        clock.setDay(new LocalDate(2024, 5, 19));
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier addonSpec2Plan2 = new PlanPhaseSpecifier("standard-ao2-weekly-plan2");
        final DateTime changePlanDateTime = clock.getUTCNow();
        addonEnt2.changePlanWithDate(new DefaultEntitlementSpecifier(addonSpec2Plan2), changePlanDateTime, Collections.emptyList(), callContext);
        assertListenerStatus();

        //PRORATED invoice corresponding to standard-ao2-weekly plan for 2024-05-15 to 2024-05-19
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2024, 5, 15), new LocalDate(2024, 5, 19), InvoiceItemType.RECURRING, new BigDecimal("4")));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setDay(new LocalDate(2024, 5, 22));
        assertListenerStatus();

        //move clock to 2025-05-22
        //RECURRING items corresponding to standard-weekly,standard-ao-weekly for 2025-05-15 to 2025-05-22 as expected
        //NO prorated RECURRING item corresponding to standard-ao2-monthly-plan2 - this in unexpected

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2024, 5, 15), new LocalDate(2024, 5, 22), InvoiceItemType.RECURRING, new BigDecimal("30")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2024, 5, 15), new LocalDate(2024, 5, 22), InvoiceItemType.RECURRING, new BigDecimal("10")));


        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setDay(new LocalDate(2024, 5, 26));
        assertListenerStatus();

        //move clock to 2025-05-22
        //RECURRING items corresponding to standard-ao2-monthly-plan2 for 2025-05-19 to 2025-05-26 - this is unexpected
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2024, 5, 19), new LocalDate(2024, 5, 26), InvoiceItemType.RECURRING, new BigDecimal("15")));
    }

    @Test
    public void testMonthlyInArrearBundleBillingAlignment() throws Exception {
        final LocalDate initialDate = new LocalDate(2024, 5, 15);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));

        //create base subscription with two addons
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("standard-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, Collections.emptyList(), callContext);
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, false, callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final PlanPhaseSpecifier addonSpec1 = new PlanPhaseSpecifier("standard-ao-monthly");
        final UUID addonEntId1 = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addonSpec1), null, null, false, Collections.emptyList(), callContext);
        final Entitlement addonEnt1 = entitlementApi.getEntitlementForId(addonEntId1, false, callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final PlanPhaseSpecifier addonSpec2 = new PlanPhaseSpecifier("standard-ao2-monthly");
        final UUID addonEntId2 = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addonSpec2), null, null, false, Collections.emptyList(), callContext);
        final Entitlement addonEnt2 = entitlementApi.getEntitlementForId(addonEntId2, false, callContext);
        assertListenerStatus();

        // Move clock to 2024-05-19 and change standard-ao2-monthly to standard-ao2-monthly-plan2
        clock.setDay(new LocalDate(2024, 5, 19));
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier addonSpec2Plan2 = new PlanPhaseSpecifier("standard-ao2-monthly-plan2");
        final DateTime changePlanDateTime = clock.getUTCNow();
        addonEnt2.changePlanWithDate(new DefaultEntitlementSpecifier(addonSpec2Plan2), changePlanDateTime, Collections.emptyList(), callContext);
        assertListenerStatus();

        // PRORATED invoice corresponding to standard-ao2-monthly plan for 2024-05-15 to 2024-05-19
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2024, 5, 15), new LocalDate(2024, 5, 19), InvoiceItemType.RECURRING, new BigDecimal("0.90")));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setDay(new LocalDate(2024, 6, 15));
        assertListenerStatus();

        //move clock to 2025-06-15
        //RECURRING items corresponding to standard-monthly,standard-ao-monthly for 2025-05-15 to 2025-06-15
        //prorated RECURRING item corresponding to standard-ao2-monthly-plan2 for 2024-05-19 to 2024-06-15 as expected
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2024, 5, 15), new LocalDate(2024, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("30")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2024, 5, 15), new LocalDate(2024, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("10")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2024, 5, 19), new LocalDate(2024, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("13.06")));
    }

}
