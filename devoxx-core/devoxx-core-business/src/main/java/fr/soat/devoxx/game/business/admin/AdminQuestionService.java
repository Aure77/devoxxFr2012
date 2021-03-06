/*
 * Copyright (c) 2011 Khanh Tuong Maudoux <kmx.petals@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package fr.soat.devoxx.game.business.admin;

import fr.soat.devoxx.game.admin.pojo.Game;
import fr.soat.devoxx.game.admin.pojo.GameUserDataManager;
import fr.soat.devoxx.game.admin.pojo.dto.QuestionRequestDto;
import fr.soat.devoxx.game.admin.pojo.exception.StorageException;
import fr.soat.devoxx.game.business.exception.QuestionServiceException;
import fr.soat.devoxx.game.business.question.Question;
import fr.soat.devoxx.game.business.question.QuestionManager;
import fr.soat.devoxx.game.business.question.Response;
import fr.soat.devoxx.game.pojo.AllQuestionResponseDto;
import fr.soat.devoxx.game.pojo.QuestionResponseDto;
import fr.soat.devoxx.game.pojo.ResponseRequestDto;
import fr.soat.devoxx.game.pojo.ResponseResponseDto;
import fr.soat.devoxx.game.pojo.question.ResponseType;
import org.dozer.DozerBeanMapper;
import org.dozer.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * User: khanh
 * Date: 21/12/11
 * Time: 15:55
 */
@Component
public class AdminQuestionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminQuestionService.class);

    private static Random RANDOM_GENERATOR = new Random();

    private final Mapper dozerMapper = new DozerBeanMapper();

    @Autowired
    QuestionManager questionManager;

    @Autowired
    GameUserDataManager gameUserDataManager;

    private final Validator validator;

    {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    public AdminQuestionService() {
    }

    AdminQuestionService(GameUserDataManager gameUserDataManager) {
        this.gameUserDataManager = gameUserDataManager;
    }

    public QuestionResponseDto getQuestion() {
        return dozerMapper.map(questionManager.loadQuestions().getRandomQuestion(), QuestionResponseDto.class);
    }

    public void addQuestion(QuestionRequestDto questionRequest) throws QuestionServiceException {
        //TODO add question processing
        throw new QuestionServiceException();
    }

    public void updateQuestion(Integer questionId, QuestionRequestDto questionRequest) throws QuestionServiceException {
        //TODO update question processing
        throw new QuestionServiceException();
    }

    public void deleteQuestion(Integer questionId) throws QuestionServiceException {
        //TODO delete question processing
        throw new QuestionServiceException();
    }

    public AllQuestionResponseDto getAllQuestionsByUser(Long userId) {
        List<Game> games = gameUserDataManager.getGamesByResultType(userId, ResponseType.NEED_RESPONSE);
        AllQuestionResponseDto results = new AllQuestionResponseDto();
        for (Game game : games) {
            results.addQuestion(dozerMapper.map(questionManager.loadQuestions().getQuestionById(game.getId()), QuestionResponseDto.class));
        }
        return results;
    }

    public ResponseResponseDto giveResponse(ResponseRequestDto responseDto) {
        Response res = dozerMapper.map(responseDto, Response.class);

        ResponseResponseDto response = new ResponseResponseDto();

        Set<ConstraintViolation<Response>> constraintViolations = validator.validate(res);
        if (constraintViolations.size() != 0) {
            LOGGER.error("Invalid response from user");
            response.setResponseType(ResponseType.INVALID);
            return response;
        }
        Question question = questionManager.loadQuestions().getQuestionById(res.getId());
        List<String> answers = question.getAnswers();
        List<String> responses = responseDto.getResponses();

        response.setId(res.getId());
        response.setAnswer(responses);
        response.setResponseType(ResponseType.FAIL);

        if (answers.size() == responses.size()) {
            //TODO : gerer le cas des majuscules/minuscules
            if (answers.containsAll(responses)) {
                response.setResponseType(ResponseType.SUCCESS);
            }
        }

        Game game = gameUserDataManager.getGameById(responseDto.getUserId(), res.getId());

        if (game == null) {
            game = new Game();
            game.setId(response.getId());
        }
        game.setGivenAnswers(response.getAnswer());
        game.setType(response.getResponseType());
        try {
            gameUserDataManager.addOrUpdateGame(responseDto.getUserId(), game);
        } catch (StorageException e) {
            LOGGER.error("unable to store result in mongoDb: {}", e.getMessage());
        }

        return response;
    }

    public void addQuestionForUser(Long userId) {
        List<Game> gamesAllReadyPlayed = gameUserDataManager.getGames(userId);
        List<Question> allQuestions = questionManager.loadQuestions().getQuestions();

        if (gamesAllReadyPlayed.size() == allQuestions.size()) {
            //NOTHING TO DO : the player allready answered all questions
//            return javax.ws.rs.core.Response.ok().build();
            return;
        }

        Question randomQuestion = getRandomQuestion(gamesAllReadyPlayed, allQuestions);

        Game game = new Game();
        game.setId(randomQuestion.getId());
        game.setType(ResponseType.NEED_RESPONSE);

        try {
            gameUserDataManager.addOrUpdateGame(userId, game);
        } catch (StorageException e) {
            LOGGER.error("unable to store result in mongoDb: {}", e.getMessage());
        }
    }

    private Question getRandomQuestion(List<Game> gamesAllReadyPlayed, List<Question> allQuestions) {
        Question tmpQuestion = null;
        for (Game game : gamesAllReadyPlayed) {
            tmpQuestion = new Question();
            tmpQuestion.setId(game.getId());
            allQuestions.remove(tmpQuestion);
        }

        return allQuestions.get(getRandomIndex(allQuestions.size()));
    }


    private int getRandomIndex(int size) {
        return RANDOM_GENERATOR.nextInt(size);
    }
}
