/*
 * Copyright (c) 2017 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.m2049r.xmrwallet.data;

import android.net.Uri;

import com.m2049r.xmrwallet.model.Wallet;
import com.m2049r.xmrwallet.util.OpenAliasHelper;

import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

public class BarcodeData {
    public static final String XMR_SCHEME = "loki:";
    public static final String XMR_PAYMENTID = "tx_payment_id";
    public static final String XMR_AMOUNT = "tx_amount";
    public static final String XMR_DESCRIPTION = "tx_description";

    public static final String OA_XMR_ASSET = "loki";


    public enum Security {
        NORMAL,
        OA_NO_DNSSEC,
        OA_DNSSEC
    }

    final public String address;
    final public String addressName;
    final public String paymentId;
    final public String amount;
    final public String description;
    final public Security security;

    public BarcodeData(String address) {
        this.address = address;
        amount = null;
        paymentId = null;
        addressName = null;
        description = null;
        this.security = Security.NORMAL;
    }

    public BarcodeData(String address, String amount) {
        this.address = address;
        this.amount = amount;
        paymentId = null;
        addressName = null;
        description = null;
        this.security = Security.NORMAL;
    }

    public BarcodeData(String address, String paymentId, String amount) {
        this.address = address;
        this.paymentId = paymentId;
        this.amount = amount;
        addressName = null;
        description = null;
        this.security = Security.NORMAL;
    }

    public BarcodeData(String address, String paymentId, String description, String amount) {
        this.address = address;
        this.paymentId = paymentId;
        this.description = description;
        this.amount = amount;
        addressName = null;
        this.security = Security.NORMAL;
    }

    public BarcodeData(String address, String addressName, String paymentId, String description, String amount, Security sec) {
        this.address = address;
        this.addressName = addressName;
        this.paymentId = paymentId;
        this.description = description;
        this.amount = amount;
        this.security = sec;
    }

    public Uri getUri() {
        return Uri.parse(getUriString());
    }

    public String getUriString() {
        StringBuilder sb = new StringBuilder();
        sb.append(BarcodeData.XMR_SCHEME).append(address);
        boolean first = true;
        if ((paymentId != null) && !paymentId.isEmpty()) {
            sb.append("?");
            first = false;
            sb.append(BarcodeData.XMR_PAYMENTID).append('=').append(paymentId);
        }
        if ((description != null) && !description.isEmpty()) {
            sb.append(first ? "?" : "&");
            first = false;
            sb.append(BarcodeData.XMR_DESCRIPTION).append('=').append(Uri.encode(description));
        }
        if ((amount != null) && !amount.isEmpty()) {
            sb.append(first ? "?" : "&");
            sb.append(BarcodeData.XMR_AMOUNT).append('=').append(amount);
        }
        return sb.toString();
    }

    static public BarcodeData fromQrCode(String qrCode) {
        // check for monero uri
        BarcodeData bcData = parseMoneroUri(qrCode);
        // check for naked monero address / integrated address
        if (bcData == null) {
            bcData = parseMoneroNaked(qrCode);
        }
        return bcData;
    }

    /**
     * Parse and decode a monero scheme string. It is here because it needs to validate the data.
     *
     * @param uri String containing a monero URL
     * @return BarcodeData object or null if uri not valid
     */

    static public BarcodeData parseMoneroUri(String uri) {
        Timber.d("parseMoneroUri=%s", uri);

        if (uri == null) return null;

        if (!uri.startsWith(XMR_SCHEME)) return null;

        String noScheme = uri.substring(XMR_SCHEME.length());
        Uri monero = Uri.parse(noScheme);
        Map<String, String> parms = new HashMap<>();
        String query = monero.getEncodedQuery();
        if (query != null) {
            String[] args = query.split("&");
            for (String arg : args) {
                String[] namevalue = arg.split("=");
                if (namevalue.length == 0) {
                    continue;
                }
                parms.put(Uri.decode(namevalue[0]).toLowerCase(),
                        namevalue.length > 1 ? Uri.decode(namevalue[1]) : "");
            }
        }
        String address = monero.getPath();

        String paymentId = parms.get(XMR_PAYMENTID);
        // deal with empty payment_id created by non-spec-conforming apps
        if ((paymentId != null) && paymentId.isEmpty()) paymentId = null;

        String description = parms.get(XMR_DESCRIPTION);
        String amount = parms.get(XMR_AMOUNT);
        if (amount != null) {
            try {
                Double.parseDouble(amount);
            } catch (NumberFormatException ex) {
                Timber.d(ex.getLocalizedMessage());
                return null; // we have an amount but its not a number!
            }
        }
        if ((paymentId != null) && !Wallet.isPaymentIdValid(paymentId)) {
            Timber.d("paymentId invalid");
            return null;
        }

        if (!Wallet.isAddressValid(address)) {
            Timber.d("address invalid");
            return null;
        }
        return new BarcodeData(address, paymentId, description, amount);
    }

    static public BarcodeData parseMoneroNaked(String address) {
        Timber.d("parseMoneroNaked=%s", address);

        if (address == null) return null;

        if (!Wallet.isAddressValid(address)) {
            Timber.d("address invalid");
            return null;
        }

        return new BarcodeData(address);
    }

    static public BarcodeData parseOpenAlias(String oaString, boolean dnssec) {
        Timber.d("parseOpenAlias=%s", oaString);
        if (oaString == null) return null;

        Map<String, String> oaAttrs = OpenAliasHelper.parse(oaString);
        if (oaAttrs == null) return null;

        String oaAsset = oaAttrs.get(OpenAliasHelper.OA1_ASSET);
        if (oaAsset == null) return null;

        String address = oaAttrs.get(OpenAliasHelper.OA1_ADDRESS);
        if (address == null) return null;

        if (OA_XMR_ASSET.equals(oaAsset)) {
            if (!Wallet.isAddressValid(address)) {
                Timber.d("XMR address invalid");
                return null;
            }
        } else {
            Timber.i("Unsupported OpenAlias asset %s", oaAsset);
            return null;
        }

        String paymentId = oaAttrs.get(OpenAliasHelper.OA1_PAYMENTID);
        String description = oaAttrs.get(OpenAliasHelper.OA1_DESCRIPTION);
        if (description == null) {
            description = oaAttrs.get(OpenAliasHelper.OA1_NAME);
        }
        String amount = oaAttrs.get(OpenAliasHelper.OA1_AMOUNT);
        String addressName = oaAttrs.get(OpenAliasHelper.OA1_NAME);

        if (amount != null) {
            try {
                Double.parseDouble(amount);
            } catch (NumberFormatException ex) {
                Timber.d(ex.getLocalizedMessage());
                return null; // we have an amount but its not a number!
            }
        }
        if ((paymentId != null) && !Wallet.isPaymentIdValid(paymentId)) {
            Timber.d("paymentId invalid");
            return null;
        }

        Security sec = dnssec ? BarcodeData.Security.OA_DNSSEC : BarcodeData.Security.OA_NO_DNSSEC;
        return new BarcodeData(address, addressName, paymentId, description, amount, sec);
    }
}
