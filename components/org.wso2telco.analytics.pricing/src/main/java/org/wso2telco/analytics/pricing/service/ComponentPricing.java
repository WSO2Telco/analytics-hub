/**
 * *****************************************************************************
 * Copyright  (c) 2015-2016, WSO2.Telco Inc. (http://www.wso2telco.com) All Rights Reserved.
 *
 * WSO2.Telco Inc. licences this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *****************************************************************************
 */
package org.wso2telco.analytics.pricing.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import org.wso2telco.analytics.pricing.AnalyticsPricingException;
import org.wso2telco.analytics.pricing.PricingObjectConstants;
import org.wso2telco.analytics.pricing.Tax;

/**
 * <TO-DO> <code>ComponentPricing</code>
 *
 * @version $Id: ComponentPricing.java,v 1.00.000
 */
public class ComponentPricing {

    private static final String CAT_DEFAULT = "__default__";

    private static void billComponent(ChargeRate rate, Map.Entry<CategoryCharge, BilledCharge> categoryEntry, List<Tax> taxList, StreamRequestData reqdata) throws AnalyticsPricingException {

        String billCategory = categoryEntry.getKey().getCategory();
        String billSubCategory = categoryEntry.getKey().getSubcategory();
        BigDecimal billRate = rate.getValue();
        Object SubsRate = null;

        switch (rate.getType()) {

            case CONSTANT:
                //operatorSubscription.setPrice(rate.getValue());
                //apply charge for category

                SubsRate = getRateSubcategory(rate, billCategory, billSubCategory);
                if (SubsRate != null) {
                    billRate = new BigDecimal((String) SubsRate);
                }
                categoryEntry.getValue().setPrice(billRate);
                applyTaxForBlockCharging(categoryEntry, rate, taxList);
                break;

            case QUOTA:

                Map<String, String> rateAttributes = (Map<String, String>) rate.getRateAttributes();

                SubsRate = getRateSubcategory(rate, billCategory, billSubCategory);

                if (SubsRate != null) {
                    rateAttributes = (Map<String, String>) SubsRate;
                }

                if (rateAttributes == null || !rateAttributes.containsKey(PricingObjectConstants.RATE_ATTRIBUTE_MAX_COUNT.toString())
                        || !rateAttributes.containsKey(PricingObjectConstants.RATE_ATTRIBUTE_EXCESS_RATE.toString()) || !rateAttributes.containsKey(PricingObjectConstants.RATE_ATTRIBUTE_DEFAULT_RATE.toString())) {
                    throw new AnalyticsPricingException("Attributes required for QUOTA charging are not specified in rate-card.xml");
                }

                int maxCount = Integer.parseInt(rateAttributes.get(PricingObjectConstants.RATE_ATTRIBUTE_MAX_COUNT.toString()));
                BigDecimal excessRate = new BigDecimal(rateAttributes.get(PricingObjectConstants.RATE_ATTRIBUTE_EXCESS_RATE.toString()));
                BigDecimal defaultRate = new BigDecimal(rateAttributes.get(PricingObjectConstants.RATE_ATTRIBUTE_DEFAULT_RATE.toString()));

                if (categoryEntry.getValue().getCount() > maxCount) {
                    int excess = categoryEntry.getValue().getCount() - maxCount;
                    BigDecimal charge = excessRate.multiply(BigDecimal.valueOf(excess)).add(defaultRate);
                    categoryEntry.getValue().setPrice(charge);
                } else {
                    categoryEntry.getValue().setPrice(defaultRate);
                }
                applyTaxForBlockCharging(categoryEntry, rate, taxList);
                break;

            case MULTITIER:
                int totalRequestCount = categoryEntry.getValue().getCount();

                BigDecimal price = BigDecimal.ZERO;
                int tierCount = 0;
                List<UsageTiers> usageTier = rate.getUsageTiers();
                //String category = categoryEntry.getKey().getCategory();
                //String subCategory = categoryEntry.getKey().getSubcategory();
                SubsRate = getRateSubcategory(rate, billCategory, billSubCategory);
                if (SubsRate != null) {
                    usageTier = (List<UsageTiers>) SubsRate;
                }

                //TODO :Implement multitier pricing
                //calculateTiersCharges(usageTier, rateCard, totalRequestCount, tierCount, operatorSubscription, subsYear, subsMonth, application, ApiName, apiVersion, categoryEntry, appId, apiId, subsId);
                break;

            case PERCENTAGE:

                applyChargesForPaymentApi(rate, categoryEntry, reqdata, taxList);

                break;

            case SUBSCRIPTION:
                //Update the Handler to count the subscribers operator wise
                //int noOfSubscribers = BillingDataAccessObject.getNoOfSubscribers(subscriber, appName, apiName);
                int noOfSubscribers = categoryEntry.getValue().getCount();
                if (SubsRate != null) {
                    billRate = new BigDecimal((String) SubsRate);
                }
                categoryEntry.getValue().setPrice(billRate.multiply(new BigDecimal(noOfSubscribers)));
                applyTaxForBlockCharging(categoryEntry, rate, taxList);
                break;

            case PER_REQUEST:
                applyChargesWithTax(categoryEntry, rate, reqdata, taxList);
                break;

            default:
                break;
        }

    }

    private static Object getRateSubcategory(ChargeRate rate, String billCategory, String billSubCategory) throws AnalyticsPricingException {

        Object categoryRate = null;
        String subcategory = CAT_DEFAULT;
        if ((billSubCategory != null) && (!billSubCategory.isEmpty())) {
            subcategory = billSubCategory;
        }

        if (!rate.getCategoryBasedVal()) {
            return categoryRate;
        }

        if ((billCategory != null) && (!billCategory.isEmpty())) {

            if (!rate.getCategories().containsKey(billCategory)) {
                throw new AnalyticsPricingException("Attributes required for charging are not specified in rate-card.xml");
            }
            Map<String, Object> subcategorymap = (Map<String, Object>) rate.getCategories().get(billCategory);
            if (!subcategorymap.containsKey(subcategory)) {
                throw new AnalyticsPricingException("Attributes required for charging are not specified in rate-card.xml");
            }
            categoryRate = subcategorymap.get(subcategory);

        }
        return categoryRate;
    }

    private static void applyTaxForBlockCharging(Map.Entry<CategoryCharge, BilledCharge> CatEntry, ChargeRate rate, List<Tax> taxList) throws AnalyticsPricingException {

        CategoryCharge categorycharge = CatEntry.getKey();
        BilledCharge billed = CatEntry.getValue();

        BigDecimal totalTax = BigDecimal.ZERO;
        //Date billingDate = Date.valueOf(year + "-" + month + "-01");    //start of the month

        for (Tax tax : taxList) {
            // totalTax += taxFraction x charge
            totalTax = totalTax.add(tax.getValue().multiply(billed.getPrice()));
        }

        CatEntry.getValue().setTax(totalTax);
    }

    private static void applyChargesForPaymentApi(ChargeRate chargeRate, Map.Entry<CategoryCharge, BilledCharge> categoryEntry, StreamRequestData reqdata,
            List<Tax> taxList) throws AnalyticsPricingException {

        ChargeRate rate = chargeRate;
        String billCategory = categoryEntry.getKey().getCategory();
        String billSubCategory = categoryEntry.getKey().getSubcategory();
        BigDecimal billRate = rate.getValue();

        boolean CategoryBased = rate.getCategoryBasedVal();

        //TO-DO Implement commision percentages pricing
        /*
        Map<String, CommissionPercentagesDTO> commisionMap = null;

        if (!CategoryBased) {
            commisionMap = BillingDataAccessObject.getCommissionPercentages(subId, appId);
        }
         */
        BigDecimal totalspcom = BigDecimal.ZERO;
        BigDecimal totalCharge = BigDecimal.ZERO;
        BigDecimal totaladscom = BigDecimal.ZERO;
        BigDecimal totalopcom = BigDecimal.ZERO;
        //BigDecimal totalPrice = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;

        // get this flag from rate card
        totalCharge = totalCharge.add(reqdata.getChargeAmount());
        //BigDecimal price = BigDecimal.ZERO;
        BigDecimal spcom = BigDecimal.ZERO;
        BigDecimal adscom = BigDecimal.ZERO;
        BigDecimal opcom = BigDecimal.ZERO;

        String category = reqdata.getCategory();
        String subcategory = reqdata.getSubcategory();
        String merchant = reqdata.getMerchant();

        //<<TODO validate category, sub category
        //CategoryEntity rateCategories = new CategoryEntity();
        //BigDecimal catpercent = rate.getValue().divide(new BigDecimal(100));

        /*
             String category = paymentRequest.getCategory();
             if (category != null && !category.isEmpty() && rateCategories.getSubCategoryMap().containsKey(category.toLowerCase())) {
             catpercent = (rateCategories.getSubCategoryMap().get(category.toLowerCase())).divide(new BigDecimal(100));;
             }
         */
        BigDecimal adscomPercnt = rate.getCommission().getAdsCommission().divide(new BigDecimal(100));
        BigDecimal spcomPercnt = rate.getCommission().getSpCommission().divide(new BigDecimal(100));
        BigDecimal opcomPercnt = rate.getCommission().getOpcoCommission().divide(new BigDecimal(100));

        if (CategoryBased) {
            Object SubsRate = getRateSubcategory(rate, billCategory, billSubCategory);
            if (SubsRate != null) {
                RateCommission commisionRates = (RateCommission) SubsRate;
                adscomPercnt = commisionRates.getAdsCommission().divide(new BigDecimal(100));
                spcomPercnt = commisionRates.getSpCommission().divide(new BigDecimal(100));
                opcomPercnt = commisionRates.getOpcoCommission().divide(new BigDecimal(100));
            }

            //  } else if (commisionMap.containsKey(merchant)) {
            //      adscomPercnt = commisionMap.get(merchant).getAdsCommission().divide(new BigDecimal(100));
            //      spcomPercnt = commisionMap.get(merchant).getSpCommission().divide(new BigDecimal(100));
            //      opcomPercnt = commisionMap.get(merchant).getOpcoCommission().divide(new BigDecimal(100));
        } else {
            throw new AnalyticsPricingException("Payment Categoreis required for MERCHANT based charging are not specified in rate-card.xml");
        }

        spcom = reqdata.getChargeAmount().multiply(spcomPercnt);
        totalspcom = totalspcom.add(spcom);

        opcom = reqdata.getChargeAmount().multiply(opcomPercnt);
        totalopcom = totalopcom.add(opcom);

        adscom = reqdata.getChargeAmount().multiply(adscomPercnt);
        totaladscom = totaladscom.add(adscom);

        //Date date = new Date(reqdata.setReqtime());
        BigDecimal totalReqTax = BigDecimal.ZERO;
        for (Tax tax : taxList) {
            //check if the date of payment request falls between this tax validity period
            totalReqTax = totalReqTax.add(tax.getValue().multiply(reqdata.getChargeAmount()));

        }
        totalTax = totalTax.add(totalReqTax);

//        if (!CategoryBased) {
//            if (opSubscription.getMerchantCharges().containsKey(merchant)) {
//                opSubscription.getMerchantCharges().get(merchant).addAdscom(adscom);
//                opSubscription.getMerchantCharges().get(merchant).addOpcom(opcom);
//                opSubscription.getMerchantCharges().get(merchant).addSpcom(spcom);
//                opSubscription.getMerchantCharges().get(merchant).addPrice(spcom);
//                opSubscription.getMerchantCharges().get(merchant).addTax(totalReqTax);
//                opSubscription.getMerchantCharges().get(merchant).addCount(1);
//            } else {
//                BilledCharge billedCharge = new BilledCharge(0);
//                billedCharge.addAdscom(adscom);
//                billedCharge.addOpcom(opcom);
//                billedCharge.addSpcom(spcom);
//                billedCharge.addPrice(spcom);
//                billedCharge.addTax(totalReqTax);
//                billedCharge.addCount(1);
//                opSubscription.getMerchantCharges().put(merchant, billedCharge);
//            }
//        }
        // Get the percentage from the rate value
        //BigDecimal percentage = rate.getValue().divide(new BigDecimal(100));
        //apply category wise charge percentage
        //BigDecimal price = totalCharge.multiply(percentage);
        //if (CategoryBased) {
        categoryEntry.getValue().addAdscom(totaladscom);
        categoryEntry.getValue().addOpcom(totalopcom);
        categoryEntry.getValue().addSpcom(totalspcom);
        categoryEntry.getValue().addPrice(totalspcom);
        categoryEntry.getValue().setTax(totalTax);

        //}
    }

    private static void applyChargesWithTax(Map.Entry<CategoryCharge, BilledCharge> CatEntry, ChargeRate rate, StreamRequestData reqdata,
            List<Tax> taxList) throws AnalyticsPricingException {

        boolean isSurcharge = false;

        //ChargeRate rate = operatorSub.getRate();
        String billCategory = CatEntry.getKey().getCategory();
        String billSubCategory = CatEntry.getKey().getSubcategory();
        BigDecimal billRate = rate.getValue();
        BigDecimal OpscomPercnt = null;

        Object SubsRate = getRateSubcategory(rate, billCategory, billSubCategory);
        if (SubsRate != null) {
            billRate = new BigDecimal((String) SubsRate);
        }

        //Surcharge value
        if (rate.getSurchargeEntity() != null) {
            billRate = new BigDecimal(rate.getSurchargeEntity().getSurchargeElementValue());
            OpscomPercnt = new BigDecimal(rate.getSurchargeEntity().getSurchargeElementOpco()).divide(new BigDecimal(100));
            isSurcharge = true;
        }

        BigDecimal totalCharge = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalOpcom = BigDecimal.ZERO;
        BigDecimal totalAdscom = BigDecimal.ZERO;

        int reqCount = 0;

        BigDecimal charge = billRate.multiply(new BigDecimal(reqdata.getResponse_count()));
        if (isSurcharge) {
            BigDecimal opcoCommision = billRate.multiply(OpscomPercnt);
            totalOpcom = totalOpcom.add(opcoCommision);
            totalAdscom = totalAdscom.add(charge.subtract(opcoCommision));
        } else {
            totalCharge = totalCharge.add(charge);
        }

        Date date = reqdata.getReqtime();
        for (Tax tax : taxList) {
            //check if the date of payment request falls between this tax validity period
            totalTax = totalTax.add(tax.getValue().multiply(charge));
        }
        reqCount++;

        CatEntry.getValue().addPrice(totalCharge);
        CatEntry.getValue().addTax(totalTax);
        CatEntry.getValue().addOpcom(totalOpcom);
        CatEntry.getValue().addAdscom(totalAdscom);

    }

}
