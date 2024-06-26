/*
 * Copyright 2010-2012 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.mock;

import java.util.Locale;
import java.util.ResourceBundle;

import org.killbill.billing.currency.api.CurrencyConversionApi;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.formatters.InvoiceFormatter;
import org.killbill.billing.invoice.plugin.api.InvoiceFormatterFactory;

public class MockInvoiceFormatterFactory implements InvoiceFormatterFactory {

    @Override
    public InvoiceFormatter createInvoiceFormatter(final String defaultLocale, final String catalogBundlePath, final Invoice invoice, final Locale locale, final CurrencyConversionApi currencyConversionApi, final ResourceBundle bundle, final ResourceBundle defaultBundle) {
        return null;
    }
}
