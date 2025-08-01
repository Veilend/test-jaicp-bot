import axios from "axios"

const RESULT_ATEMPT_TIMEOUT_MS = 5000;
const MAX_ATEMPTS = 10;
const RELEVANT_SOURCES_SUFFIX = "Источники, на основе которых был получен ответ:"

function headers(){
    const token = $secrets.get("khub_token", "Не указан публичный ключ KHUB, secret khub_token");
    const headers = {
        "Authorization": "Bearer " + token,
        "Content-Type": "application/json"
    };
    return headers;
}

function sleep(ms) {
    return new Promise(resolve => {
        setTimeout(resolve, ms);
    })
}

async function createChat(baseUrl){
    const projectId = await $session.projectId;
    const body = {"name": "jaicp_" + projectId};
    try {
        const res = await axios.post(
            `${baseUrl}/api/knowledge-hub/chat`,
            body,
            {headers: headers()}
        );
        log.info(`>>> KHUB API Create chat res.data: ${JSON.stringify(res.data)}`);
        return res.data.id;
    } catch(e) {
        throw new Error(">>> Error calling KHUB API Create chat for chatId: " + JSON.stringify(e));
    }
}

async function makeRequest(baseUrl, message, chatId){
    const body = {"query": message};

    try {
        const res = await axios.post(
            `${baseUrl}/api/knowledge-hub/async/chat/${chatId}/query`,
            body,
            {headers: headers()}
        );
        log.info(`>>> KHUB API Make request res.data: ${JSON.stringify(res.data)}`);
        return res.data;
    } catch(e) {
        throw new Error(">>> Error calling KHUB API Make request: " + toPrettyString(e));
    }
}

async function getChatQueryResult(baseUrl, chatId, recordId) {
    const projectId = await $session.projectId;

    try {
        const res = await axios.get(
            `${baseUrl}/api/knowledge-hub/chat/${chatId}/query/${recordId}`,
            {headers: headers()}
        );
        log.info(`>>> KHUB API getChatQueryResult res.data: ${JSON.stringify(res.data)}`);
        return res.data;
    } catch(e) {
        throw new Error(">>> Error calling KHUB API getChatQueryResult: " + toPrettyString(e));
    }
}

function is_query_finished(answer){
	if (["FINISHED", "FAILED", "CANCELED"].includes(answer.status)) {
		return true;
	} else {
		return false;
	}
}

function parseAnswer(answer) {
    if (answer.relevantSources && answer.response) {
        let text_answer = answer.response;
        text_answer = `${text_answer}\n${RELEVANT_SOURCES_SUFFIX}`;
        let i = 0;
        while (i < answer.relevantSources.length) {
            const relevantSource = answer.relevantSources[i];
            if (relevantSource.externalLink) {
                let sourceTitle = relevantSource.path;
                let isExternalIntegrationSource = relevantSource.path.split("/").length > 1;
                if (isExternalIntegrationSource) {
                    sourceTitle = relevantSource.path.split("/").slice(-1).join().split(".").shift();
                }
                text_answer += `\n- [${sourceTitle}](${relevantSource.externalLink})`;
            } else {
                text_answer += `\n- ${relevantSource.path}`;
            }
            i++;
        }
        return text_answer;
    } else {
        return answer.response;
    }
}

async function ask(baseUrl, request) {
    const chatId = await $client.chatId;
    const chatRes = await makeRequest(baseUrl, request, chatId);
    const recordId = chatRes.id;

    let answer = await getChatQueryResult(baseUrl, chatId, recordId);
	let attempts = MAX_ATEMPTS;
	while (!is_query_finished(answer) && attempts > 0) {
		answer = await getChatQueryResult(baseUrl, chatId, recordId);
		attempts--;
		await sleep(RESULT_ATEMPT_TIMEOUT_MS);
	}
    if (answer.status === "FINISHED") return parseAnswer(answer);
    else throw new Error("Waiting too long for a response from KnowledgeHub. Query status: " + answer.status);
}

export default { createChat, ask }

