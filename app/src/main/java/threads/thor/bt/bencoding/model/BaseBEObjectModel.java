package threads.thor.bt.bencoding.model;

import java.util.List;

import threads.thor.bt.bencoding.model.rule.Rule;

abstract class BaseBEObjectModel implements BEObjectModel {

    private final List<Rule> rules;

    BaseBEObjectModel(List<Rule> rules) {
        this.rules = rules;
    }

    @Override
    public final ValidationResult validate(Object object) {

        ValidationResult result;
        if (object != null) {

            // unwrap BEObjects
            if (object instanceof BEObject) {
                object = ((BEObject) object).getValue();
            }

            Class<?> javaType = TypesMapping.getJavaTypeForBEType(getType());
            if (!javaType.isAssignableFrom(object.getClass())) {
                result = new ValidationResult();
                result.addMessage("Wrong type -- expected " + javaType.getName()
                        + ", actual: " + object.getClass().getName());
                return result;
            }
        }

        result = validateObject(object);
        // fail-fast if any rules failed
        return result.isSuccess() ? afterValidate(result, object) : result;
    }

    private ValidationResult validateObject(Object object) {
        ValidationResult result = new ValidationResult();
        rules.stream()
                .filter(rule -> !rule.validate(object))
                .map(Rule::getDescription)
                .forEach(result::addMessage);
        return result;
    }

    /**
     * Contribute to the validation result.
     *
     * @since 1.0
     */
    protected abstract ValidationResult afterValidate(ValidationResult validationResult, Object object);
}
