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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultBaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.entitlement.api.Subscription;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestDateConversionsSubscription extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testCreateSubscriptionWithoutDatesUTC() throws Exception {
        // This test demonstrates that when no dates are specified at the time of subscription creation,
        // the current UTC time is used as the subscription start date irrespective of the account time zone
        final DateTime initialDateTime = new DateTime(2024, 1,1,3,30);
        clock.setTime(initialDateTime);
        final DateTime referenceTime = new DateTime(2024, 1,1,6,0);


        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1, DateTimeZone.UTC, referenceTime));

        //create subscription
        final PlanPhaseSpecifier planSpec = new PlanPhaseSpecifier("pistol-monthly-notrial");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(planSpec, null, null, null, null), "externalKey", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        assertEquals(subscription.getEffectiveStartDate().compareTo(initialDateTime), 0);
    }

    @Test(groups = "slow")
    public void testCreateSubscriptionWithoutDatesPST() throws Exception {
        // This test demonstrates that when no dates are specified at the time of subscription creation,
        // the current UTC time is used as the subscription start date irrespective of the account time zone
        final DateTime initialDateTime = new DateTime(2024, 1,1,3,30);
        clock.setTime(initialDateTime);
        final DateTime referenceTime = new DateTime(2024, 1,1,6,0);


        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1, DateTimeZone.forID("America/Los_Angeles"), referenceTime));

        //create subscription
        final PlanPhaseSpecifier planSpec = new PlanPhaseSpecifier("pistol-monthly-notrial");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(planSpec, null, null, null, null), "externalKey", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        assertEquals(subscription.getEffectiveStartDate().compareTo(initialDateTime), 0);
    }

    @Test(groups = "slow")
    public void testCreateSubscriptionWithLocalDateUTC() throws Exception {
        // This test demonstrates that when a LocalDate is specified at the time of subscription creation,
        // it is taken to be in the user's timezone and is converted to a DateTime in UTC timezone using the reference local time.
        final DateTime initialDateTime = new DateTime(2024, 1,1,3,30);
        clock.setTime(initialDateTime);
        final DateTime referenceTime = new DateTime(2024, 1,1,6,0);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1, DateTimeZone.UTC, referenceTime));

        //create subscription
        final PlanPhaseSpecifier planSpec = new PlanPhaseSpecifier("pistol-monthly-notrial");
        final LocalDate startDate = new LocalDate(2024, 1,2); //Taken to be in the user's time zone, so converted into a DateTime using referenceLocalTime. Since referenceTime=6:00, referenceLocalTime=6:00, hence 2024-01-02 => 2024-01-02T6:00 UTC
        final DateTime startDateWithReferenceTimeInUTC = new DateTime(2024, 1, 2, 6,0); //2024-01-02T6:00 UTC => 2024-01-02T6:00 UTC
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(planSpec, null, null, null, null), "externalKey", startDate, startDate, false, false, Collections.emptyList(), callContext);

        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        assertEquals(subscription.getBillingStartDate().compareTo(startDateWithReferenceTimeInUTC), 0);
    }

    @Test(groups = "slow")
    public void testCreateSubscriptionWithLocalDatePST() throws Exception {
        // This test demonstrates that when a LocalDate is specified at the time of subscription creation,
        // it is taken to be in the user's timezone and is converted to a DateTime in UTC timezone using the reference local time.
        final DateTime initialDateTime = new DateTime(2024, 1,1,3,30);
        clock.setTime(initialDateTime);
        final DateTime referenceTime = new DateTime(2024, 1,1,6,0);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1, DateTimeZone.forID("America/Los_Angeles"), referenceTime));

        //create subscription
        final PlanPhaseSpecifier planSpec = new PlanPhaseSpecifier("pistol-monthly-notrial");
        final LocalDate startDate = new LocalDate(2024, 1,2); //Taken to be in the user's time zone, so converted into a DateTime using referenceLocalTime. Since referenceTime=6:00, referenceLocalTime=23:00, hence 2024-01-02 => 2024-01-02T23:00 PST
        final DateTime startDateWithReferenceTimeInUTC = new DateTime(2024, 1, 3, 6,0); //2024-01-02T23:00 PST => 2024-01-03T6:00 UTC
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(planSpec, null, null, null, null), "externalKey", startDate, startDate, false, false, Collections.emptyList(), callContext);

        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        assertEquals(subscription.getBillingStartDate().compareTo(startDateWithReferenceTimeInUTC), 0);
    }

    @Test(groups = "slow")
    public void testCreateSubscriptionWithDateTimeUTC() throws Exception {
        // This test demonstrates that when a DateTime is specified at the time of subscription creation,
        // it is considered to be in UTC and used as it is
        final DateTime initialDateTime = new DateTime(2024, 1,1,3,30);
        clock.setTime(initialDateTime);
        final DateTime referenceTime = new DateTime(2024, 1,1,6,0);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1, DateTimeZone.UTC, referenceTime));

        //create subscription
        final DateTime startDateTime = new DateTime(2024, 1, 2, 11,0);

        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, null, List.of(new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("pistol-monthly-notrial"))), startDateTime, startDateTime, false);
        final List<UUID> entitlementIds = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), List.of(baseEntitlementWithAddOnsSpecifier), true, Collections.emptyList(), callContext);

        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementIds.get(0), false, callContext);
        assertEquals(subscription.getBillingStartDate().compareTo(startDateTime), 0);
    }

    @Test(groups = "slow")
    public void testCreateSubscriptionWithDateTimePST() throws Exception {
        // This test demonstrates that when a DateTime is specified at the time of subscription creation,
        // it is considered to be in UTC and used as it is
        final DateTime initialDateTime = new DateTime(2024, 1,1,3,30);
        clock.setTime(initialDateTime);
        final DateTime referenceTime = new DateTime(2024, 1,1,6,0);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1, DateTimeZone.forID("America/Los_Angeles"), referenceTime));

        //create subscription
        final DateTime startDateTime = new DateTime(2024, 1, 2, 11,0);

        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, null, List.of(new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("pistol-monthly-notrial"))), startDateTime, startDateTime, false);
        final List<UUID> entitlementIds = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), List.of(baseEntitlementWithAddOnsSpecifier), true, Collections.emptyList(), callContext);

        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementIds.get(0), false, callContext);
        assertEquals(subscription.getBillingStartDate().compareTo(startDateTime), 0);
    }



}
