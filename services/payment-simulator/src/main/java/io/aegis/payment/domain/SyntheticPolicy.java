package io.aegis.payment.domain;

/** Retained policy inputs, not an executor or a public scenario selector. */
public record SyntheticPolicy(String version,String scenario,String chosenOutcome) {
    public SyntheticPolicy { if(version==null||version.isBlank()||scenario==null||scenario.isBlank()||chosenOutcome==null)throw new IllegalArgumentException("policy inputs required"); }
    public static SyntheticPolicy normal() { return new SyntheticPolicy("AEGIS_SUCCESS_V1","NORMAL_SUCCESS","SUCCEEDED"); }
}
