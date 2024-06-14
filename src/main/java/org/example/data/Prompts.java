package org.example.data;

public class Prompts {

    public static String systemStringForCallChatGPTSetUp = "You are an Android code analysis expert. Please help me understand the information being sent through the Networking API.";

    public static String userStringForCallChatGPTSetUp_Retrofit = "Please answer the following questions one by one:\n" +
            "1. Can you understand all the information being sent through the '<METHOD_NAME_TO_BE_REPLACE>' method?\n" +
            "2. Answer all the information being sent with descriptions. If the answer to question 1 is NO, then leave it blank.\n" +
            "3. Answer what additional code you need to review in order to answer question 2. You can review the DEFINITION or USAGES of any class, method, variable, or constant in the provided Java code. That is, you need to determine the name of a class/method/variable/constant you want to review, and whether the operation is to view its DEFINITION or USAGES. And you can only review one class/method/variable/constant at each time. If the answer to question 1 is YES, then leave it blank.\n" +
            "Answer in the following JSON format:\n" +
            "```json\n" +
            "{\"Question_1\": {\"answer\": \"YES/NO\"}, \"Question_2\": [{\"info\": <INFO>, \"des\": <DESCRIPTION>}], \"Question_3\": {\"name\": <NAME>, \"type\": \"CLASS/METHOD/VARIABLE/CONSTANT\", \"operation\": \"DEFINITION/USAGES\"}}\n" +
            "```";

    public static String userStringForCallChatGPTSetUp = "Please answer the following questions one by one:\n" +
            "1. Can you understand all the information being sent through the '<METHOD_NAME_TO_BE_REPLACE>' method? You should provide justification to illustrate whether the information being sent is clear (e.g., geolocation, credit card number) to understand or hazy (e.g., str, jsonObject) that you can not understand.\n" +
            "2. Answer all the information being sent with descriptions. If the answer to question 1 is NO, then leave it blank.\n" +
            "3. Answer what additional code you need to review in order to answer question 2. You can review the DEFINITION or USAGES of any class, method, variable, or constant in the provided Java code. That is, you need to determine the name of a class/method/variable/constant you want to review the most, and whether the operation is to view its DEFINITION or USAGES. To avoid confusion in recognizing the name of class/method/variable/constant, you should provide its full name identical to the code if there are different classes/methods/variables/constants having the same name. For example, if you want to review the 'a' method in the line 'a(b);' while there is another 'a' method in another line 'public void a(int i) {', the name you should answer is 'a(b)' instead of 'a'. You should provide justification to illustrate where the info being sent is from and how you should trace the info. If the answer to question 1 is YES, then leave it blank.\n" +
            "Answer in the following JSON format:\n" +
            "```json\n" +
            "{\"Question_1\": {\"answer\": \"YES/NO\", \"justification\": <JUSTIFICATION>}, \"Question_2\": [{\"info\": <INFO>, \"des\": <DESCRIPTION>}], \"Question_3\": {\"name\": <NAME>, \"type\": \"CLASS/METHOD/VARIABLE/CONSTANT\", \"operation\": \"DEFINITION/USAGES\", \"justification\": <JUSTIFICATION>}}\n" +
            "```";

    public static String systemStringForAnalyseInfo = "You are an Android privacy expert. Please help me understand the information being collected by the application through the network requests.";


    public static String userStringForAnalyseInfo = "You'll be provided with the information with descriptions. Please categorize the information into appropriate categories and data types.\n" +
            "\n" +
            "There are following categories and data types defined in the Google Play's Data safety section: (each line in the format: category: [data type])\n" +
            "Location: [Approximate location, Precise location]\n" +
            "Personal info: [Name, Email address, User IDs, Address, Phone number, Race and ethnicity, Political or religious beliefs, Sexual orientation, Other info]\n" +
            "Financial info: [User payment info, Purchase history, Credit score, Other financial info]\n" +
            "Health and fitness: [Health info, Fitness info], Messages: [Emails, SMS or MMS, Other in-app messages]\n" +
            "Photos and videos: [Photos, Videos]\n" +
            "Audio files: [Voice or sound recordings, Music files, Other audio files]\n" +
            "Files and docs: [Files and docs]\n" +
            "Calendar: [Calendar events]\n" +
            "Contacts: [Contacts]\n" +
            "App activity: [App interactions, In-app search history, Installed apps, Other user-generated content, Other actions]\n" +
            "Web browsing: [Web browsing history]\n" +
            "App info and performance: [Crash logs, Diagnostics, Other app performance data]\n" +
            "Device or other IDs: [Device or other IDs].\n" +
            "\n" +
            "If the information doesn't fit into any category or data type above, you should answer 'N/A'. Justify your answer by considering the meaning of the information and the definitions in the Google Play's Data safety section.\n" +
            "\n" +
            "Answer in JSON format:\n" +
            "```json\n" +
            "[{\"info\": <INFO>, \"category\": <CATEGORY>, \"data_type\": <DATA_TYPE>, \"justification\": <JUSTIFICATION>}]\n" +
            "```";

    public static String codeNotAvailableNote = "/* The additional Java code you want to review is not available, but you may still try to answer the info being sent or review other code. If you would like to review other code, you'd better change another name. Please answer in JSON format. */";

    public static String maxDepthReachNote = "/* The additional Java code you want to review is not available, so you MUST try to answer the info being sent as much as you can. Therefore, your answer to the question 1 is YES, and you should answer the question 2. Please answer in JSON format. */";


}
