require: functions.js
    type = scriptEs6
    name = khub

require: answers.yaml
    var = $Answers

init:
    bind("onAnyError", function($context) {
        $reactions.answer(_.sample($Answers["Errors"]));
    });

theme: /

    state: Start
        q!: $regex</start>
        scriptEs6:
            $jsapi.startSession();
            if ($client.chatId) {
                delete $client.chatId;
            }
            $session.khub_baseurl = "https://khub.just-ai.com";
        random: 
            a: Здравствуйте! Я помогу найти ответы на вопросы по вашей Базе Знаний.
            
    state: Education
        intentGroup!: /KnowledgeBase/FAQ.Общие вопросы
        script: $faq.pushReplies();

    state: Question
        q: задать другой вопрос || fromState = "/KHub", onlyThisState = true
        random:
            a: Спросите меня, например…
            a: Я могу ответить на вопрос о том…
            a: Задайте вопрос про то…
        script:
            $reactions.buttons(_.sample($Answers["ExampleQuestions"], 3));

    state: GetQuestion
        event!: noMatch
        scriptEs6:
            $analytics.setSessionData("Question", $request.query);
                $conversationApi.sendTextToClient(
            _.sample($Answers["PreparingAnswer"])
                );
        go!: /KHub
        scriptEs6:
            const secretName = "Baza_znaniy_voprosotvet_SHS"
            $client.ragChat_Baza_znaniy_voprosotvet_SHS = $client.ragChat_Baza_znaniy_voprosotvet_SHS || await $rag.chat.create(secretName);
            $rag.chat.processQuery({secretName, chatId: $client.ragChat_Baza_znaniy_voprosotvet_SHS.id, query: $request.query})
              .then(chatResponse => {
                  $conversationApi.sendTextToClient(chatResponse.response);
              })
              .catch(error => {
                  $conversationApi.sendTextToClient("Error: " + error);
              });
    
    state: NoMatch
        event!: noMatch
        a: Не могу найти ответ на этот вопрос в FAQ.

    state: KHub
        scriptEs6:
            $client.chatId = $client.chatId || await khub.createChat($session.khub_baseurl);
            khub.ask($session.khub_baseurl, $request.query)
                .then(response => {
                    $conversationApi.sendRepliesToClient([
                        {
                            "type": "text",
                            "text": response
                        },
                        {
                            "type": "buttons",
                            "buttons": [
                                {
                                    "text": "Задать другой вопрос"
                                },
                                {
                                    "text": "Оценить ответ"
                                }
                            ]
                        }
                    ]);
                })
                .catch(error => {
                    $conversationApi.sendRepliesToClient([
                        {
                            "type": "text",
                            "text": "К сожалению, я не смог обработать ваш запрос из-за ошибки. Я обязательно передам её разработчикам. Пожалуйста, попробуйте задать вопрос еще раз."
                        },
                        {
                            "type": "buttons",
                            "buttons": [
                                {
                                    "text": "Задать другой вопрос"
                                }
                            ]
                        }
                    ]);
                })
                .finally(() => {
                    $session.answered = true;
                } )


        state: Waiting || noContext = true
            event: noMatch
            if: $session.answered
                script: delete $session.answered;
                go!: /GetQuestion
            else:
                random:
                    a: Пожалуйста, подождите еще немного. Прямо сейчас ищу информацию по вашему запросу...
                    a: Вот-вот отвечу на ваш вопрос, подождите еще чуть-чуть...

    state: Feedback
        q: оценить ответ || fromState = "/KHub", onlyThisState = true
        random:
            a: Ваша обратная связь поможет мне стать лучше! Пожалуйста, оцените мой ответ.
            a: Насколько мой ответ был полезен для вас?
            a: Не могли бы вы оценить, насколько мой ответ решил вашу задачу?
        buttons:
            "5 – отлично" -> ./5
            "4 – хорошо" -> ./4
            "3 – удовлетворительно" -> ./3
            "2 – могло быть лучше" -> ./2
            "1 – неудовлетворительно" -> ./1

        state: 1
            q: * (1/один/0/ноль) *
            script: $analytics.setNps(1);
            go!: /NegativeFeedback

        state: 2
            q: * (2/два) *
            script: $analytics.setNps(2);
            go!: /NegativeFeedback

        state: 3
            q: * (3/три) *
            script: $analytics.setNps(3);
            go!: /NegativeFeedback

        state: 4
            q: * (4/четыре) *
            script: $analytics.setNps(4);
            go!: /PositiveFeedback

        state: 5
            q: * (5/пять) *
            script: $analytics.setNps(5);
            go!: /PositiveFeedback

    state: NegativeFeedback
        random:
            a: Спасибо. Мои разработчики проверят, что пошло не так, и они обязательно все исправят.
            a: Спасибо за ваш отзыв. Я передам его разработчикам, чтобы они исправили ошибку.
        script:
            $analytics.setSessionResult("Negative feedback");

    state: PositiveFeedback
        random:
            a: Спасибо за обратную связь! Мне очень приятно, что я смог помочь вам.
            a: Спасибо за отзыв. Если у вас появятся еще вопросы, я с радостью на них отвечу!
        script:
            $analytics.setSessionResult("Positive feedback");