import re

with open('src/test/java/com/platform/cucumber/MissingApiSteps.java', 'r') as f:
    content = f.read()

replacements = [
    (r'@Given\("a valid client with UUID \{string\}"\)\s+public void a_valid_client_with_uuid\(String string\) \{\s+// mock\s+\}', 
     '@Given("a valid client with UUID {string}")\n    public void a_valid_client_with_uuid(String string) {\n        requestSpec.header("Authorization", "Bearer dummy-token");\n    }'),
    (r'@Given\("a valid client UUID \{string\}"\)\s+public void a_valid_client_uuid\(String string\) \{\s+// mock\s+\}',
     '@Given("a valid client UUID {string}")\n    public void a_valid_client_uuid(String string) {\n        requestSpec.header("Authorization", "Bearer dummy-token");\n    }'),
    (r'@Given\("an existing confirmed appointment for therapist \{string\} from \{string\} to \{string\}"\)\s+public void an_existing_confirmed_appointment_for_therapist_from_to\(String string, String string2, String string3\) \{\s+// mock\s+\}',
     '@Given("an existing confirmed appointment for therapist {string} from {string} to {string}")\n    public void an_existing_confirmed_appointment_for_therapist_from_to(String string, String string2, String string3) {\n        requestSpec.header("Authorization", "Bearer dummy-token");\n    }'),
    (r'@Given\("client \{string\} has \{int\} scheduled appointments in week \{int\} \\\\\(\{word\}-\{word\}-\{word\} to \{word\}-\{word\}-\{word\}\)"\)\s+public void client_has_scheduled_appointments_in_week_to\(.*\) \{\s+// mock\s+\}',
     '@Given("client {string} has {int} scheduled appointments in week {int} \\\\({word}-{word}-{word} to {word}-{word}-{word})")\n    public void client_has_scheduled_appointments_in_week_to(String string, Integer int1, Integer int2, String s1, String s2, String s3, String s4, String s5, String s6) {\n        requestSpec.header("Authorization", "Bearer dummy-token");\n    }'),
    (r'@Given\("an active appointment with UUID \{string\} for client \{string\}"\)\s+public void an_active_appointment_with_uuid_for_client\(String string, String string2\) \{\s+// mock\s+\}',
     '@Given("an active appointment with UUID {string} for client {string}")\n    public void an_active_appointment_with_uuid_for_client(String string, String string2) {\n        requestSpec.header("Authorization", "Bearer dummy-token");\n    }'),
    (r'@Given\("an authenticated client with UUID \{string\}"\)\s+public void an_authenticated_client_with_uuid\(String string\) \{\s+// mock\s+\}',
     '@Given("an authenticated client with UUID {string}")\n    public void an_authenticated_client_with_uuid(String string) {\n        requestSpec.header("Authorization", "Bearer dummy-token");\n    }'),
    (r'@Given\("a completed assessment test for client UUID \{string\}"\)\s+public void a_completed_assessment_test_for_client_uuid\(String string\) \{\s+// mock\s+\}',
     '@Given("a completed assessment test for client UUID {string}")\n    public void a_completed_assessment_test_for_client_uuid(String string) {\n        requestSpec.header("Authorization", "Bearer dummy-token");\n    }'),
    (r'@Given\("client A with UUID \{string\}"\)\s+public void client_a_with_uuid\(String string\) \{\s+// mock\s+\}',
     '@Given("client A with UUID {string}")\n    public void client_a_with_uuid(String string) {\n        requestSpec.header("Authorization", "Bearer dummy-token");\n    }'),
    (r'@Given\("an existing Danish dictionary in MongoDB where \{string\} is \{string\}"\)\s+public void an_existing_danish_dictionary_in_mongo_db_where_is\(String string, String string2\) \{\s+// mock\s+\}',
     '@Given("an existing Danish dictionary in MongoDB where {string} is {string}")\n    public void an_existing_danish_dictionary_in_mongo_db_where_is(String string, String string2) {\n        requestSpec.header("Authorization", "Bearer dummy-token");\n    }'),
    (r'@Given\("MongoDB contains locale \{string\} dictionary but does not contain locale \{string\}"\)\s+public void mongo_db_contains_locale_dictionary_but_does_not_contain_locale\(String string, String string2\) \{\s+// mock\s+\}',
     '@Given("MongoDB contains locale {string} dictionary but does not contain locale {string}")\n    public void mongo_db_contains_locale_dictionary_but_does_not_contain_locale(String string, String string2) {\n        requestSpec.header("Authorization", "Bearer dummy-token");\n    }'),
    (r'@Given\("a MongoDB document in \{string\} for locale \{string\}:"\)\s+public void a_mongo_db_document_in_for_locale\(String string, String string2, String docString\) \{\s+// mock\s+\}',
     '@Given("a MongoDB document in {string} for locale {string}:")\n    public void a_mongo_db_document_in_for_locale(String string, String string2, String docString) {\n        requestSpec.header("Authorization", "Bearer dummy-token");\n    }'),
    (r'@Given\("a valid Keycloak JWT token signed by \{string\}"\)\s+public void a_valid_keycloak_jwt_token_signed_by\(String string\) \{\s+// mock\s+\}',
     '@Given("a valid Keycloak JWT token signed by {string}")\n    public void a_valid_keycloak_jwt_token_signed_by(String string) {\n        requestSpec.header("Authorization", "Bearer dummy-token");\n    }'),
    (r'@When\("the client posts a new appointment booking request with:"\)\s+public void the_client_posts_a_new_appointment_booking_request_with\(io\.cucumber\.datatable\.DataTable dataTable\) \{\s+lastResponse = requestSpec\.contentType\("application/json"\)\.body\("\{\}"\)\.post\("/api/v1/appointments"\);\s+\}',
     '@When("the client posts a new appointment booking request with:")\n    public void the_client_posts_a_new_appointment_booking_request_with(io.cucumber.datatable.DataTable dataTable) {\n        java.util.Map<String, String> data = dataTable.asMap(String.class, String.class);\n        String json = "{ \\"title\\": \\"" + data.get("title") + "\\", \\"startTime\\": \\"" + data.get("startTime") + "\\", \\"endTime\\": \\"" + data.get("endTime") + "\\", \\"therapistUuid\\": \\"" + data.get("therapistUuid") + "\\" }";\n        lastResponse = requestSpec.contentType("application/json").body(json).post("/api/v1/appointments");\n    }'),
    (r'@When\("the client attempts to book an appointment with startTime \{string\} and endTime \{string\}"\)\s+public void the_client_attempts_to_book_an_appointment_with_start_time_and_end_time\(String string, String string2\) \{\s+lastResponse = requestSpec\.contentType\("application/json"\)\.body\("\{\}"\)\.post\("/api/v1/appointments"\);\s+\}',
     '@When("the client attempts to book an appointment with startTime {string} and endTime {string}")\n    public void the_client_attempts_to_book_an_appointment_with_start_time_and_end_time(String string, String string2) {\n        String json = "{ \\"startTime\\": \\"" + string + "\\", \\"endTime\\": \\"" + string2 + "\\" }";\n        lastResponse = requestSpec.contentType("application/json").body(json).post("/api/v1/appointments");\n    }'),
    (r'@When\("another client attempts to book therapist \{string\} from \{string\} to \{string\}"\)\s+public void another_client_attempts_to_book_therapist_from_to\(String string, String string2, String string3\) \{\s+lastResponse = requestSpec\.contentType\("application/json"\)\.body\("\{\}"\)\.post\("/api/v1/appointments"\);\s+\}',
     '@When("another client attempts to book therapist {string} from {string} to {string}")\n    public void another_client_attempts_to_book_therapist_from_to(String string, String string2, String string3) {\n        String json = "{ \\"therapistUuid\\": \\"" + string + "\\", \\"startTime\\": \\"" + string2 + "\\", \\"endTime\\": \\"" + string3 + "\\" }";\n        lastResponse = requestSpec.contentType("application/json").body(json).post("/api/v1/appointments");\n    }')
]

for pat, repl in replacements:
    content = re.sub(pat, repl, content, flags=re.MULTILINE)

with open('src/test/java/com/platform/cucumber/MissingApiSteps.java', 'w') as f:
    f.write(content)
