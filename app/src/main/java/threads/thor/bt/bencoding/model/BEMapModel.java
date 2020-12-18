package threads.thor.bt.bencoding.model;

import java.util.List;
import java.util.Map;

import threads.thor.bt.bencoding.BEType;
import threads.thor.bt.bencoding.model.rule.Rule;

class BEMapModel extends BaseBEObjectModel {

    private final Map<String, BEObjectModel> entriesModel;

    BEMapModel(Map<String, BEObjectModel> entriesModel, List<Rule> rules) {
        super(rules);
        this.entriesModel = entriesModel;
    }

    @Override
    public BEType getType() {
        return BEType.MAP;
    }

    @Override
    protected ValidationResult afterValidate(ValidationResult validationResult, Object object) {

        if (object != null) {

            Map<?, ?> map = (Map<?, ?>) object;
            for (Map.Entry<String, BEObjectModel> entryModel : entriesModel.entrySet()) {

                String key = entryModel.getKey();
                entryModel.getValue().validate(map.get(key)).getMessages()
                        .forEach(validationResult::addMessage);
            }
        }

        return validationResult;
    }
}
