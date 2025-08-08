package com.demo.sequence.model;

/**
 * Numbering Strategy Configuration
 * 
 * Defines how sequence numbers are generated and managed for different
 * invoice types across sites and partitions.
 */
public class NumberingStrategy {

    public enum StrategyType {
        GLOBAL_SEQUENTIAL,      // Single global sequence across all sites/partitions
        SITE_SEQUENTIAL,        // Separate sequence per site
        PARTITION_SEQUENTIAL,   // Separate sequence per site-partition combination
        CUSTOM                  // Custom strategy implementation
    }

    private String name;
    private String description;
    private StrategyType type;
    private long startNumber;
    private int increment;
    private boolean gapRecovery;
    private String format;
    private ValidationRules validation;

    // Constructors
    public NumberingStrategy() {
        this.type = StrategyType.GLOBAL_SEQUENTIAL;
        this.startNumber = 1L;
        this.increment = 1;
        this.gapRecovery = true;
        this.format = "{number}";
    }

    public NumberingStrategy(String name, StrategyType type) {
        this();
        this.name = name;
        this.type = type;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private NumberingStrategy strategy = new NumberingStrategy();

        public Builder name(String name) {
            strategy.name = name;
            return this;
        }

        public Builder description(String description) {
            strategy.description = description;
            return this;
        }

        public Builder type(StrategyType type) {
            strategy.type = type;
            return this;
        }

        public Builder startNumber(long startNumber) {
            strategy.startNumber = startNumber;
            return this;
        }

        public Builder increment(int increment) {
            strategy.increment = increment;
            return this;
        }

        public Builder gapRecovery(boolean gapRecovery) {
            strategy.gapRecovery = gapRecovery;
            return this;
        }

        public Builder format(String format) {
            strategy.format = format;
            return this;
        }

        public Builder validation(ValidationRules validation) {
            strategy.validation = validation;
            return this;
        }

        public NumberingStrategy build() {
            return strategy;
        }
    }

    // Validation Rules nested class
    public static class ValidationRules {
        private long minValue;
        private long maxValue;
        private boolean allowNegative;
        private boolean enforceUniqueness;

        public ValidationRules() {
            this.minValue = 1L;
            this.maxValue = Long.MAX_VALUE;
            this.allowNegative = false;
            this.enforceUniqueness = true;
        }

        // Getters and setters
        public long getMinValue() { return minValue; }
        public void setMinValue(long minValue) { this.minValue = minValue; }
        
        public long getMaxValue() { return maxValue; }
        public void setMaxValue(long maxValue) { this.maxValue = maxValue; }
        
        public boolean isAllowNegative() { return allowNegative; }
        public void setAllowNegative(boolean allowNegative) { this.allowNegative = allowNegative; }
        
        public boolean isEnforceUniqueness() { return enforceUniqueness; }
        public void setEnforceUniqueness(boolean enforceUniqueness) { this.enforceUniqueness = enforceUniqueness; }
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public StrategyType getType() {
        return type;
    }

    public void setType(StrategyType type) {
        this.type = type;
    }

    public long getStartNumber() {
        return startNumber;
    }

    public void setStartNumber(long startNumber) {
        this.startNumber = startNumber;
    }

    public int getIncrement() {
        return increment;
    }

    public void setIncrement(int increment) {
        this.increment = increment;
    }

    public boolean isGapRecovery() {
        return gapRecovery;
    }

    public void setGapRecovery(boolean gapRecovery) {
        this.gapRecovery = gapRecovery;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public ValidationRules getValidation() {
        return validation;
    }

    public void setValidation(ValidationRules validation) {
        this.validation = validation;
    }

    // Utility methods
    public String formatSequenceNumber(long sequenceNumber) {
        return format.replace("{number}", String.valueOf(sequenceNumber));
    }

    public boolean isValidSequenceNumber(long sequenceNumber) {
        if (validation == null) {
            return sequenceNumber >= startNumber;
        }
        
        return sequenceNumber >= validation.getMinValue() && 
               sequenceNumber <= validation.getMaxValue() &&
               (validation.isAllowNegative() || sequenceNumber >= 0);
    }

    @Override
    public String toString() {
        return "NumberingStrategy{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", startNumber=" + startNumber +
                ", increment=" + increment +
                ", gapRecovery=" + gapRecovery +
                ", format='" + format + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumberingStrategy that = (NumberingStrategy) o;

        if (startNumber != that.startNumber) return false;
        if (increment != that.increment) return false;
        if (gapRecovery != that.gapRecovery) return false;
        if (!name.equals(that.name)) return false;
        if (type != that.type) return false;
        return format.equals(that.format);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + (int) (startNumber ^ (startNumber >>> 32));
        result = 31 * result + increment;
        result = 31 * result + (gapRecovery ? 1 : 0);
        result = 31 * result + format.hashCode();
        return result;
    }
}