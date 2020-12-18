package threads.thor.bt.bencoding.model;

import java.util.List;

import threads.thor.bt.bencoding.BEType;
import threads.thor.bt.bencoding.model.rule.Rule;

class BEListModel extends BaseBEObjectModel {

    private final BEObjectModel elementModel;

    BEListModel(BEObjectModel elementModel, List<Rule> rules) {
        super(rules);
        this.elementModel = elementModel;
    }

    @Override
    public BEType getType() {
        return BEType.LIST;
    }

    @Override
    protected ValidationResult afterValidate(ValidationResult validationResult, Object object) {

        if (object != null) {
            List<?> list = (List<?>) object;
            for (Object element : list) {
                elementModel.validate(element).getMessages().forEach(validationResult::addMessage);
            }
        }

        return validationResult;
    }
}
