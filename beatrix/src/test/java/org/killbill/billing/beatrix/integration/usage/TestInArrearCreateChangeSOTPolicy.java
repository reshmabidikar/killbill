/*
 * Copyright 2020-2025 Equinix, Inc
 * Copyright 2014-2025 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration.usage;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultBaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestInArrearCreateChangeSOTPolicy extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testInArrearCreateChangeSOTPolicy");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testCreateChangeSOTPolicy() throws Exception {

        final LocalDate today = new LocalDate(2025, 3, 20);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(20));

        //CREATE SUBSCRIPTION BUNDLE WITH BASE AND 2 ADDONs, 2 VARADDONS
        final EntitlementSpecifier baseEntitlementSpecifier = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("standard-monthly-in-arrear"));
        final EntitlementSpecifier addOnEntitlementSpecifier1 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("ao1-in-arrear"));
        final EntitlementSpecifier addOnEntitlementSpecifier2 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("ao2-in-arrear"));
        final EntitlementSpecifier addOnEntitlementSpecifier3 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("var-ao1-in-arrear"));
        final EntitlementSpecifier addOnEntitlementSpecifier4 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("var-ao2-in-arrear"));
        final List<EntitlementSpecifier> specifierList = List.of(baseEntitlementSpecifier, addOnEntitlementSpecifier1, addOnEntitlementSpecifier2, addOnEntitlementSpecifier3, addOnEntitlementSpecifier4);
        final BaseEntitlementWithAddOnsSpecifier cartSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, null, specifierList, null, null, false);
        final List<BaseEntitlementWithAddOnsSpecifier> entitlementWithAddOnsSpecifierList = List.of(cartSpecifier);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.CREATE, NextEvent.CREATE, NextEvent.CREATE, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final List<UUID> allEntitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(allEntitlements.get(0), false, callContext);
        final Entitlement addOnEntitlement1 = entitlementApi.getEntitlementForId(allEntitlements.get(1), false, callContext);
        final Entitlement addOnEntitlement2 = entitlementApi.getEntitlementForId(allEntitlements.get(2), false, callContext);
        Entitlement varAddOnEntitlement1 = entitlementApi.getEntitlementForId(allEntitlements.get(3), false, callContext);
        Entitlement varAddOnEntitlement2 = entitlementApi.getEntitlementForId(allEntitlements.get(4), false, callContext);

        //RECORD USAGE FOR VARADDON1
        clock.setDay(new LocalDate(2025, 4, 1));
        recordUsageData(varAddOnEntitlement1.getId(), "tracking-1", "bullets", clock.getUTCNow(), BigDecimal.valueOf(5L), callContext);

        //RECORD USAGE FOR VARADDON2
        clock.setDay(new LocalDate(2025, 4, 5));
        recordUsageData(varAddOnEntitlement1.getId(), "tracking-2", "bullets", clock.getUTCNow(), BigDecimal.valueOf(5L), callContext);

        clock.setDay(new LocalDate(2025, 4, 6));

        //CHANGE VARADDON1 -> VARADDON3 AND VARADDON2 -> VARADDON4 WITH START OF TERM EFFECTIVE_DATE-2025-03-20
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.NULL_INVOICE, NextEvent.CREATE, NextEvent.NULL_INVOICE);
        EntitlementSpecifier aoSpec = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("var-ao3-in-arrear", PhaseType.EVERGREEN), null, null, null, null);
        varAddOnEntitlement1.changePlanWithDate(aoSpec, today, Collections.emptyList(), callContext);
        aoSpec = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("var-ao4-in-arrear", PhaseType.EVERGREEN), null, null, null, null);
        varAddOnEntitlement2.changePlanWithDate(aoSpec, today, Collections.emptyList(), callContext);
        assertListenerStatus();

        varAddOnEntitlement1 = entitlementApi.getEntitlementForId(varAddOnEntitlement1.getId(), false, callContext);
        Assert.assertEquals(varAddOnEntitlement1.getLastActivePlan().getName(), "var-ao3-in-arrear");

        varAddOnEntitlement2 = entitlementApi.getEntitlementForId(varAddOnEntitlement2.getId(), false, callContext);
        Assert.assertEquals(varAddOnEntitlement2.getLastActivePlan().getName(), "var-ao4-in-arrear");

        //NO INVOICE GENERATED - IS THIS EXPECTED? OR SHOULD INVOICE BE GENERATED DUE TO PLAN CHANGE?
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), true, true, true, callContext);
        Assert.assertEquals(invoices.size(), 0);

        //MOVE TO 2025-04-20
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2025, 4, 20));
        assertListenerStatus();

        //INVOICE GENERATED WITH USAGE ITEMS
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), true, true, true, callContext);
        Assert.assertEquals(invoices.size(), 1);

        List<ExpectedInvoiceItemCheck> expectedInvoiceItems = List.of(new ExpectedInvoiceItemCheck(new LocalDate(2025, 3, 20), new LocalDate(2025, 4, 20), InvoiceItemType.RECURRING, new BigDecimal("10")),
                                                                      new ExpectedInvoiceItemCheck(new LocalDate(2025, 3, 20), new LocalDate(2025, 4, 20), InvoiceItemType.RECURRING, new BigDecimal("1.24")),
                                                                      new ExpectedInvoiceItemCheck(new LocalDate(2025, 3, 20), new LocalDate(2025, 4, 20), InvoiceItemType.RECURRING, new BigDecimal("3.7")),
                                                                      new ExpectedInvoiceItemCheck(new LocalDate(2025, 3, 20), new LocalDate(2025, 4, 20), InvoiceItemType.RECURRING, new BigDecimal("3.2")),
                                                                      new ExpectedInvoiceItemCheck(new LocalDate(2025, 3, 20), new LocalDate(2025, 4, 20), InvoiceItemType.RECURRING, new BigDecimal("2.7")),
                                                                      new ExpectedInvoiceItemCheck(new LocalDate(2025, 3, 20), new LocalDate(2025, 4, 20), InvoiceItemType.USAGE, new BigDecimal("10")),
                                                                      new ExpectedInvoiceItemCheck(new LocalDate(2025, 3, 20), new LocalDate(2025, 4, 20), InvoiceItemType.USAGE, new BigDecimal("0")));

        invoiceChecker.checkInvoice(invoices.get(0), callContext, expectedInvoiceItems);




    }


}
