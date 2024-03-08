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

package org.killbill.billing.beatrix.integration.usage;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultBaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestInArrearUsagePST extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testInArrearUsagePST() throws Exception {
        clock.setTime(new DateTime(2024, 2, 1, 13, 25));

        final AccountData accountData = getAccountData(31, DateTimeZone.forID("America/Los_Angeles"));
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DateTime subStartDateTime = new DateTime(2024, 2, 1, 6, 30);

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("pistol-in-arrear-monthly-notrial");
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("bullets-usage-in-arrear");

        final List<EntitlementSpecifier> specifierList = List.of(new DefaultEntitlementSpecifier(baseSpec), new DefaultEntitlementSpecifier(addOnSpec));
        final BaseEntitlementWithAddOnsSpecifier cartSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, "key1", specifierList, subStartDateTime, subStartDateTime, false);
        final List<BaseEntitlementWithAddOnsSpecifier> entitlementWithAddOnsSpecifierList = List.of(cartSpecifier);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK,
                                      NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final List<UUID> allEntitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Trigger invoice dry run
        final LocalDate targetDate = new LocalDate(2024, 02, 29);
        final DryRunArguments dryRunArgs = new TestDryRunArguments(DryRunType.TARGET_DATE);
        final Invoice invoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), targetDate, dryRunArgs, Collections.emptyList(), callContext);
        assertNotNull(invoice);
        assertNotNull(invoice.getInvoiceItems());
        assertEquals(invoice.getInvoiceItems().size(), 2);

        final List<ExpectedInvoiceItemCheck> toBeChecked =
                List.of(new ExpectedInvoiceItemCheck(new LocalDate(2024, 1, 31), new LocalDate(2024, 2, 29), InvoiceItemType.RECURRING, new BigDecimal("100")),
                        new ExpectedInvoiceItemCheck(new LocalDate(2024, 1, 31), new LocalDate(2024, 2, 29), InvoiceItemType.USAGE, BigDecimal.ZERO));
        invoiceChecker.checkInvoiceNoAudits(invoice, toBeChecked);
    }

    @Test(groups = "slow")
    public void testInArrearUsageCreateCancelUTC() throws Exception {
        DateTime today = new DateTime(2024, 2, 21, 6, 00); //2024-02-16T6:00 UTC=2024-02-15T10:30 PST (different days)
        clock.setTime(today);

        final AccountData accountData = getAccountData(21);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("pistol-in-arrear-monthly-notrial");
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("bullets-usage-in-arrear");

        final List<EntitlementSpecifier> specifierList = List.of(new DefaultEntitlementSpecifier(baseSpec), new DefaultEntitlementSpecifier(addOnSpec));
        final BaseEntitlementWithAddOnsSpecifier cartSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, "key1", specifierList, null, null, false);
        final List<BaseEntitlementWithAddOnsSpecifier> entitlementWithAddOnsSpecifierList = List.of(cartSpecifier);

        //create subscription at 6:05
        clock.setTime(today.plusMinutes(5));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final List<UUID> allEntitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(allEntitlements.get(0), false, callContext);
        final Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(allEntitlements.get(1), false, callContext);

        //Record usage at 6:30
        clock.setTime(today.plusMinutes(30));
        recordUsageData(addOnEntitlement.getId(), "t1", "bullets", clock.getUTCNow(), BigDecimal.valueOf(85L), callContext);

        //cancel base at 6:40
        clock.setTime(today.plusMinutes(40));
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.CANCEL, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        baseEntitlement.cancelEntitlementWithPolicy(EntitlementActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Invoice generated for the usage item
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2024, 2, 21), new LocalDate(2024, 2, 21), InvoiceItemType.USAGE, new BigDecimal("2.95")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t1"), internalCallContext);

        //Trigger invoice dry run for next month - no invoice
        final LocalDate targetDate = new LocalDate(2024, 3, 21);
        final DryRunArguments dryRunArgs = new TestDryRunArguments(DryRunType.TARGET_DATE);
        try {
            final Invoice invoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), targetDate, dryRunArgs, Collections.emptyList(), callContext);
        }
        catch(InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.INVOICE_NOTHING_TO_DO.getCode());
        }

        //Move clock to 2024-03-21T6:40 - still no invoice
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testInArrearUsageCreateCancelPST() throws Exception { //PST and UTC on different days
        DateTime today = new DateTime(2024, 2, 21, 6, 00); //2024-02-16T6:00 UTC=2024-02-15T10:30 PST (different days)
        clock.setTime(today);

        DateTime referenceTime = new DateTime(2024, 2, 20, 9, 0);
        final AccountData accountData = getAccountData(20, DateTimeZone.forID("America/Los_Angeles"), referenceTime);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("pistol-in-arrear-monthly-notrial");
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("bullets-usage-in-arrear");

        final List<EntitlementSpecifier> specifierList = List.of(new DefaultEntitlementSpecifier(baseSpec), new DefaultEntitlementSpecifier(addOnSpec));
        final BaseEntitlementWithAddOnsSpecifier cartSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, "key1", specifierList, null, null, false);
        final List<BaseEntitlementWithAddOnsSpecifier> entitlementWithAddOnsSpecifierList = List.of(cartSpecifier);

        //create subscription at 6:05
        clock.setTime(today.plusMinutes(5));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final List<UUID> allEntitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(allEntitlements.get(0), false, callContext);
        final Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(allEntitlements.get(1), false, callContext);

        //Record usage at 6:30
        clock.setTime(today.plusMinutes(30));
        recordUsageData(addOnEntitlement.getId(), "t1", "bullets", clock.getUTCNow(), BigDecimal.valueOf(85L), callContext);

        //cancel base at 6:40
        clock.setTime(today.plusMinutes(40));
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.CANCEL, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        baseEntitlement.cancelEntitlementWithPolicy(EntitlementActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2024, 2, 20), new LocalDate(2024, 2, 20), InvoiceItemType.USAGE, new BigDecimal("2.95")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t1"), internalCallContext);

        //Trigger invoice dry run for next month
        final LocalDate targetDate = new LocalDate(2024, 3, 20);
        final DryRunArguments dryRunArgs = new TestDryRunArguments(DryRunType.TARGET_DATE);
        try {
            final Invoice invoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), targetDate, dryRunArgs, Collections.emptyList(), callContext);
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.INVOICE_NOTHING_TO_DO.getCode());
        }

        //Move to next month - still no invoice
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testInArrearUsageCreateChangeCancelAddOnPST1() throws Exception {
        //2024-03-04T9:00 UTC=2024-03-04T1:00 PST (Before DST)
        final DateTime today = new DateTime(2024, 3, 4, 9, 00);
        clock.setTime(today);

        final DateTime referenceTime = new DateTime(2024, 3, 4, 9, 0);
        final AccountData accountData = getAccountData(4, DateTimeZone.forID("America/Los_Angeles"), referenceTime);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("pistol-in-arrear-monthly-notrial");
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("bullets-usage-in-arrear");

        final List<EntitlementSpecifier> specifierList = List.of(new DefaultEntitlementSpecifier(baseSpec), new DefaultEntitlementSpecifier(addOnSpec));
        final BaseEntitlementWithAddOnsSpecifier cartSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, "key1", specifierList, null, null, false);
        final List<BaseEntitlementWithAddOnsSpecifier> entitlementWithAddOnsSpecifierList = List.of(cartSpecifier);

        //create subscription at 9:05
        clock.setTime(today.plusMinutes(5));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final List<UUID> allEntitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(allEntitlements.get(0), false, callContext);
        final Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(allEntitlements.get(1), false, callContext);

        //Trigger invoice dry run for 2024-04-04
        final LocalDate targetDate = new LocalDate(2024, 4, 4);
        final DryRunArguments dryRunArgs = new TestDryRunArguments(DryRunType.TARGET_DATE);
        final Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), targetDate, dryRunArgs, Collections.emptyList(), callContext);
        assertNotNull(dryRunInvoice);
        assertNotNull(dryRunInvoice.getInvoiceItems());
        assertEquals(dryRunInvoice.getInvoiceItems().size(), 2);

        //$0 usage item as expected
        List<ExpectedInvoiceItemCheck> toBeChecked =
                List.of(new ExpectedInvoiceItemCheck(new LocalDate(2024, 3, 4), new LocalDate(2024, 4, 4), InvoiceItemType.RECURRING, new BigDecimal("100")),
                        new ExpectedInvoiceItemCheck(new LocalDate(2024, 3, 4), new LocalDate(2024, 4, 4), InvoiceItemType.USAGE, BigDecimal.ZERO));

        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, toBeChecked);

        //record usages
        recordUsageData(addOnEntitlement.getId(), "t1", "bullets", new DateTime(2024, 3, 15, 2, 0), BigDecimal.valueOf(10L), callContext);
        recordUsageData(addOnEntitlement.getId(), "t2", "bullets", new DateTime(2024, 4, 4, 6, 30), BigDecimal.valueOf(20L), callContext);
        //Recorded at 2024-04-04T7:30 AM UTC so 2024-04-03T11:30 PM PST - Before midnight - so included in current invoice
        recordUsageData(addOnEntitlement.getId(), "t3", "bullets", new DateTime(2024, 4, 4, 7, 30), BigDecimal.valueOf(10L), callContext);
        //Recorded at 2024-04-04T8:30 AM UTC so 2024-04-04T12:30 AM PST - After midnight - so included in next invoice
        recordUsageData(addOnEntitlement.getId(), "t4", "bullets", new DateTime(2024, 4, 4, 8, 30), BigDecimal.valueOf(30L), callContext);

        //Move clock to 2024-04-04 - invoice generated. Only t1,t2 and t3 are included as these are recorded before midnight PST
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
        toBeChecked =
                List.of(new ExpectedInvoiceItemCheck(new LocalDate(2024, 3, 4), new LocalDate(2024, 4, 4), InvoiceItemType.RECURRING, new BigDecimal("100")),
                        new ExpectedInvoiceItemCheck(new LocalDate(2024, 3, 4), new LocalDate(2024, 4, 4), InvoiceItemType.USAGE, new BigDecimal("2.95")));

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext, toBeChecked);
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t1", "t2", "t3"), internalCallContext);

        //Move clock to 2024-05-04 - invoice generated. t4 is included as this is recorded after midnight PST
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        toBeChecked =
                List.of(new ExpectedInvoiceItemCheck(new LocalDate(2024, 4, 4), new LocalDate(2024, 5, 4), InvoiceItemType.RECURRING, new BigDecimal("100")),
                        new ExpectedInvoiceItemCheck(new LocalDate(2024, 4, 4), new LocalDate(2024, 5, 4), InvoiceItemType.USAGE, new BigDecimal("2.95")));

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext, toBeChecked);
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t4"), internalCallContext);

        //Change addon plan EOT (2024-06-04)
        LocalDate endOfTermDate = clock.getUTCToday().plusMonths(1);
        addOnEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("bullets-usage-in-arrear2"), null, null, UUID.randomUUID().toString(), null), endOfTermDate, Collections.emptyList(), callContext);

        //record usages
        recordUsageData(addOnEntitlement.getId(), "t5", "bullets", new DateTime(2024, 5, 15, 2, 0), BigDecimal.valueOf(10L), callContext);
        recordUsageData(addOnEntitlement.getId(), "t6", "bullets", new DateTime(2024, 6, 4, 6, 30), BigDecimal.valueOf(20L), callContext);
        //Recorded at 2024-06-04T7:30 AM UTC so 2024-06-03T11:30 PM PST - Before midnight - so included in current invoice
        recordUsageData(addOnEntitlement.getId(), "t7", "bullets", new DateTime(2024, 6, 4, 7, 30), BigDecimal.valueOf(10L), callContext);
        //Recorded at 2024-06-04T8:30 AM UTC so 2024-06-04T12:30 AM PST - After midnight - so included in next invoice
        recordUsageData(addOnEntitlement.getId(), "t8", "bullets", new DateTime(2024, 6, 4, 8, 30), BigDecimal.valueOf(30L), callContext);

        //Move clock to 2024-06-04 - invoice generated. t5,t6,t7,t8 are included - is this expected?
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();
        toBeChecked =
                List.of(new ExpectedInvoiceItemCheck(new LocalDate(2024, 5, 4), new LocalDate(2024, 6, 4), InvoiceItemType.RECURRING, new BigDecimal("100")),
                        new ExpectedInvoiceItemCheck(new LocalDate(2024, 5, 4), new LocalDate(2024, 6, 4), InvoiceItemType.USAGE, new BigDecimal("2.95")),
                        new ExpectedInvoiceItemCheck(new LocalDate(2024, 6, 4), new LocalDate(2024, 6, 4), InvoiceItemType.USAGE, new BigDecimal("2.95")));

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext, toBeChecked);
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t5", "t6", "t7", "t8"), internalCallContext);

        //Move clock to 2024-07-04 - invoice generated. No usage
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        toBeChecked =
                List.of(new ExpectedInvoiceItemCheck(new LocalDate(2024, 6, 4), new LocalDate(2024, 7, 4), InvoiceItemType.RECURRING, new BigDecimal("100")),
                        new ExpectedInvoiceItemCheck(new LocalDate(2024, 6, 4), new LocalDate(2024, 7, 4), InvoiceItemType.USAGE, BigDecimal.ZERO));

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext, toBeChecked);

        //cancel subscription at EOT (2024-08-04)
        endOfTermDate = clock.getUTCToday().plusMonths(1);
        baseEntitlement.cancelEntitlementWithDate(endOfTermDate, true, Collections.emptyList(), callContext);

        //record usages
        recordUsageData(addOnEntitlement.getId(), "t9", "bullets", new DateTime(2024, 7, 15, 2, 0), BigDecimal.valueOf(10L), callContext);
        recordUsageData(addOnEntitlement.getId(), "t10", "bullets", new DateTime(2024, 8, 4, 6, 30), BigDecimal.valueOf(20L), callContext);
        //Recorded at 2024-07-04T7:30 AM UTC so 2024-07-03T11:30 PM PST - Before midnight - so included in current invoice
        recordUsageData(addOnEntitlement.getId(), "t11", "bullets", new DateTime(2024, 8, 4, 7, 30), BigDecimal.valueOf(10L), callContext);
        //Recorded at 2024-07-04T8:30 AM UTC so 2024-07-04T12:30 AM PST - After midnight - But still included in current invoice as the subscription is cancelled??
        recordUsageData(addOnEntitlement.getId(), "t12", "bullets", new DateTime(2024, 8, 4, 8, 30), BigDecimal.valueOf(30L), callContext);

        //Move clock to 2024-08-04 - invoice generated. t9,t10,t11,t12 are included. Is this expected?
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();
        toBeChecked =
                List.of(new ExpectedInvoiceItemCheck(new LocalDate(2024, 7, 4), new LocalDate(2024, 8, 4), InvoiceItemType.RECURRING, new BigDecimal("100")),
                        new ExpectedInvoiceItemCheck(new LocalDate(2024, 7, 4), new LocalDate(2024, 8, 4), InvoiceItemType.USAGE, new BigDecimal("3.95")),
                        new ExpectedInvoiceItemCheck(new LocalDate(2024, 8, 4), new LocalDate(2024, 8, 4), InvoiceItemType.USAGE, new BigDecimal("3.95")));

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 5, callContext, toBeChecked);
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t9", "t10", "t11", "t12"), internalCallContext);

        //Move clock to 2024-08-04 - no invoice generated as subscription is cancelled
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

    }

}
