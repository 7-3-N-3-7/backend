import re

with open('src/test/java/com/platform/cucumber/MissingApiSteps.java', 'r') as f:
    content = f.read()

# Add imports if they don't exist
if 'I18nDictionaryRepository' not in content:
    content = content.replace('public class MissingApiSteps {', '''import org.springframework.beans.factory.annotation.Autowired;
import com.platform.repository.I18nDictionaryRepository;
import com.platform.entity.I18nDictionary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.HashMap;

public class MissingApiSteps {

    @Autowired
    private I18nDictionaryRepository i18nRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
''')

# Now replace the specific methods
content = re.sub(
    r'@Given\("a MongoDB document in \{string\} for locale \{string\}:"\)\s+public void a_mongo_db_document_in_for_locale\(String string, String string2, String docString\) \{\s+requestSpec\.header\("Authorization", "Bearer dummy-token"\);\s+\}',
    '''@Given("a MongoDB document in {string} for locale {string}:")
    public void a_mongo_db_document_in_for_locale(String string, String string2, String docString) throws Exception {
        Map<String, Object> doc = objectMapper.readValue(docString, Map.class);
        Map<String, String> translations = (Map<String, String>) doc.get("translations");
        I18nDictionary dict = new I18nDictionary(string2, translations);
        i18nRepository.save(dict);
    }''',
    content
)

content = re.sub(
    r'@Given\("an existing Danish dictionary in MongoDB where \{string\} is \{string\}"\)\s+public void an_existing_danish_dictionary_in_mongo_db_where_is\(String string, String string2\) \{\s+requestSpec\.header\("Authorization", "Bearer valid-token-for-admin"\);\s+\}',
    '''@Given("an existing Danish dictionary in MongoDB where {string} is {string}")
    public void an_existing_danish_dictionary_in_mongo_db_where_is(String string, String string2) {
        requestSpec.header("Authorization", "Bearer valid-token-for-admin");
        Map<String, String> translations = new HashMap<>();
        translations.put(string, string2);
        I18nDictionary dict = new I18nDictionary("da", translations);
        i18nRepository.save(dict);
    }''',
    content
)

content = re.sub(
    r'@Given\("MongoDB contains locale \{string\} dictionary but does not contain locale \{string\}"\)\s+public void mongo_db_contains_locale_dictionary_but_does_not_contain_locale\(String string, String string2\) \{\s+requestSpec\.header\("Authorization", "Bearer dummy-token"\);\s+\}',
    '''@Given("MongoDB contains locale {string} dictionary but does not contain locale {string}")
    public void mongo_db_contains_locale_dictionary_but_does_not_contain_locale(String string, String string2) {
        Map<String, String> translations = new HashMap<>();
        translations.put("fallback_key", "fallback_value");
        I18nDictionary dict = new I18nDictionary(string, translations);
        i18nRepository.save(dict);
        i18nRepository.findByLocale(string2).ifPresent(d -> i18nRepository.delete(d));
    }''',
    content
)

content = re.sub(
    r'@Then\("the response body should contain JSON dictionary mapping \{string\} to \{string\}"\)\s+public void the_response_body_should_contain_json_dictionary_mapping_to\(String string, String string2\) \{\s+// mock\s+\}',
    '''@Then("the response body should contain JSON dictionary mapping {string} to {string}")
    public void the_response_body_should_contain_json_dictionary_mapping_to(String string, String string2) {
        lastResponse.then().body(string, org.hamcrest.Matchers.equalTo(string2));
    }''',
    content
)

content = re.sub(
    r'@Then\("subsequent GET requests to \{string\} should immediately return \{string\} for \{string\}"\)\s+public void subsequent_get_requests_to_should_immediately_return_for\(String string, String string2, String string3\) \{\s+lastResponse = requestSpec\.when\(\)\.get\(string\);\s+// mock verify\s+\}',
    '''@Then("subsequent GET requests to {string} should immediately return {string} for {string}")
    public void subsequent_get_requests_to_should_immediately_return_for(String string, String string2, String string3) {
        lastResponse = requestSpec.when().get(string);
        lastResponse.then().body(string3, org.hamcrest.Matchers.equalTo(string2));
    }''',
    content
)


with open('src/test/java/com/platform/cucumber/MissingApiSteps.java', 'w') as f:
    f.write(content)
