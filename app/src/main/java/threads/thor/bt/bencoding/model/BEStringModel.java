package threads.thor.bt.bencoding.model;

import java.util.List;

import threads.thor.bt.bencoding.BEType;
import threads.thor.bt.bencoding.model.rule.Rule;

class BEStringModel extends BaseBEObjectModel {

    BEStringModel(List<Rule> rules) {
        super(rules);
    }

    @Override
    public BEType getType() {
        return BEType.STRING;
    }

    @Override
    protected ValidationResult afterValidate(ValidationResult validationResult, Object object) {
        return validationResult;
    }
}
