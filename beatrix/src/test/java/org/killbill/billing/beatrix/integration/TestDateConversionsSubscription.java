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
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Subscription;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestDateConversionsSubscription extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testCreateSubscriptionWithoutDatesUTC() throws Exception {
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
        assertEquals(subscription.getBillingStartDate().compareTo(initialDateTime), 0);
    }

    @Test(groups = "slow")
    public void testCreateSubscriptionWithoutDatesPST() throws Exception {
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
        assertEquals(subscription.getBillingStartDate().compareTo(initialDateTime), 0);
    }


}
