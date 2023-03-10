package com.cooksys.twitter_api.service.impl;

import com.cooksys.twitter_api.dtos.*;
import com.cooksys.twitter_api.entities.Tweet;
import com.cooksys.twitter_api.entities.User;
import com.cooksys.twitter_api.exceptions.BadRequestException;
import com.cooksys.twitter_api.exceptions.NotFoundException;
import com.cooksys.twitter_api.helpers.SortByPostedReverse;
import com.cooksys.twitter_api.helpers.SortBySizeReverse;
import com.cooksys.twitter_api.mappers.HashtagMapper;
import com.cooksys.twitter_api.mappers.TweetMapper;
import com.cooksys.twitter_api.mappers.UserMapper;
import com.cooksys.twitter_api.repositories.HashtagRepository;
import com.cooksys.twitter_api.repositories.TweetRepository;
import com.cooksys.twitter_api.repositories.UserRepository;
import com.cooksys.twitter_api.service.TweetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.cooksys.twitter_api.helpers.Helpers.*;


@Service
@RequiredArgsConstructor
public class TweetServiceImpl implements TweetService {


    private final UserRepository userRepository;
    private final TweetMapper tweetMapper;
    private final TweetRepository tweetRepository;
    private final UserMapper userMapper;
    private final HashtagRepository hashtagRepository;
    private final HashtagMapper hashtagMapper;

    /**
     * Creates a new simple tweet, with the author set to the user identified by the credentials in the request body.
     * If the given credentials do not match an active user in the database, an error should be sent in lieu of a
     * response.
     * <p>
     * The response should contain the newly-created tweet.
     * Because this always creates a simple tweet, it must have a content property and may not have inReplyTo or
     * repostOf properties.
     * <p>
     * IMPORTANT: when a tweet with content is created, the server must process the tweet's content for @{username}
     * mentions and #{hashtag} tags. There is no way to create hashtags or create mentions from the API, so this must be
     * handled automatically!
     * <p>
     * Request
     * {
     * content: 'string',
     * credentials: 'Credentials'
     * }
     * Response
     * 'Tweet'
     */
    @Override
    public TweetResponseDto createTweet(TweetRequestDto tweetRequestDto) {

        CredentialsDto credentialsDto = tweetRequestDto.getCredentials();
        // 1. If the given credentials do not match an active user in the database, an error should be sent
        // 2. It must have a content property
        // Both are handled in the helper.
        if (!isValidTweetRequestDto(tweetRequestDto)) {
            throw new BadRequestException("Malformed tweet request");
        }

        // Get the author user if it's active
        Optional<User> optionalAuthor = userRepository.findByCredentialsUsernameAndDeletedFalse(credentialsDto.getUsername());

        // If the given credentials do not match an active user in the database, an error should be sent
        if (!credentialsAreCorrect(optionalAuthor, credentialsDto)) {
            throw new BadRequestException("Bad credentials or user is not active");
        }

        //     * IMPORTANT: when a tweet with content is created, the server must process the tweet's content for @{username}
        //     * mentions and #{hashtag} tags. There is no way to create hashtags or create mentions from the API, so this must be
        //     * handled automatically!

        Tweet tweet = tweetMapper.dtoToEntity(tweetRequestDto);
        tweet.setAuthor(optionalAuthor.get());
        optionalAuthor.get().getTweets().add(tweet);
        tweet.setPosted(new Timestamp(System.currentTimeMillis()));
        Tweet savedTweet = tweetRepository.saveAndFlush(tweet);
        parseAndSaveMentions(savedTweet, tweetRepository, userRepository); // inject dependencies
        parseAndSaveHashtags(savedTweet, tweetRepository, hashtagRepository); // inject dependencies
        return tweetMapper.entityToDto(savedTweet);
    }


    @Override
    public TweetResponseDto replyToTweet(Long id, TweetRequestDto tweetRequestDto) {
        // step 1 - get tweetToBeRepliedTo by id

        Optional<Tweet> tweetToBeRepliedTo = tweetRepository.findByIdAndDeletedFalse(id);

        // step 2 - check if tweetRequestDto is null -> if so throw exception

        if (tweetRequestDto == null) {
            throw new BadRequestException("Bad tweet request dto");
        }

        // step 3 - check if tweetToBeRepliedTo is deleted or doesn't exist and throw an error

        if (tweetToBeRepliedTo.isEmpty()) {
            throw new BadRequestException("Bad request");
        }

        Optional<User> optionalUser = userRepository.findByCredentialsUsernameAndDeletedFalse(tweetRequestDto.getCredentials().getUsername());

        // step 4 - check if the given credentials match the credentials
        if (!credentialsAreCorrect(optionalUser, tweetRequestDto.getCredentials())) {
            throw new BadRequestException("BAD");
        }

        Tweet tweet = tweetMapper.dtoToEntity(tweetRequestDto);
        tweet.setInReplyTo(tweetToBeRepliedTo.get());
        tweet.setPosted(new Timestamp(System.currentTimeMillis()));
        tweet.setAuthor(optionalUser.get());
        parseAndSaveHashtags(tweet, tweetRepository, hashtagRepository);
        parseAndSaveMentions(tweet, tweetRepository, userRepository);
        return tweetMapper.entityToDto(tweetRepository.saveAndFlush(tweet));
    }


    @Override
    public TweetResponseDto repostTweet(Long id, CredentialsDto credentialsDto) {
        if (credentialsDto == null) {
            throw new BadRequestException("Bad Credentials DTO");
        }
        Optional<User> tweetAuthor = userRepository.findByCredentialsUsernameAndDeletedFalse(credentialsDto.getUsername());
        if (!credentialsAreCorrect(tweetAuthor, credentialsDto)) {
            throw new BadRequestException("Bad credentials or user is not active");
        }

        Optional<Tweet> optionalTweet = tweetRepository.findByIdAndDeletedFalse(id);
        if (optionalTweet.isEmpty()) {
            throw new NotFoundException("No tweet found");
        }
        Tweet tweet = new Tweet();
        tweet.setAuthor(tweetAuthor.get());
        tweet.setRepostOf(optionalTweet.get());
        return tweetMapper.entityToDto(tweetRepository.saveAndFlush(tweet));
    }

    /**
     * GET tweets/{id}/tags
     * <p>
     * Retrieves the tags associated with the tweet with the given id. If that tweet is deleted or otherwise doesn't exist, an error should be sent in lieu of a response.
     * <p>
     * IMPORTANT Remember that tags and mentions must be parsed by the server!
     * <p>
     * Response ['Hashtag']
     */
    @Override
    public List<HashtagDto> getTags(Long id) {
        Optional<Tweet> oT = tweetRepository.findByIdAndDeletedFalse(id);
        if (oT.isEmpty()) {
            throw new BadRequestException("no tweet @ id");
        }
        return hashtagMapper.entitiesToDtos(oT.get().getHashtagList());
    }


    @Override
    public List<TweetResponseDto> getReposts(Long id) {
        Optional<Tweet> optionalTweet = tweetRepository.findByIdAndDeletedFalse(id);
        if (optionalTweet.isEmpty()) {
            throw new NotFoundException("Tweet not found with id: " + id);
        }
        List<Tweet> allTweets = tweetRepository.findAllByDeletedFalse();
        ArrayList<Tweet> result = new ArrayList<>();


        for (Tweet tweet : allTweets) {
            if (tweet.getRepostOf() != null && tweet.getRepostOf().equals(optionalTweet.get())) {
                result.add(tweet);
            }
        }
        return tweetMapper.entitiesToDtos(result);
    }

    @Override
    public List<UserResponseDto> getMentions(Long id) {
        Optional<Tweet> optionalTweet = tweetRepository.findByIdAndDeletedFalse(id);
        if (optionalTweet.isEmpty()) {
            throw new NotFoundException("Tweet not found with id: " + id);
        }
        List<User> mentionsUserList = new ArrayList<>(optionalTweet.get().getMentionsUserlist());

        ArrayList<User> del = new ArrayList<>();
        for (User u : mentionsUserList) {
            if (u.isDeleted()) {
                del.add(u);
            }
        }
        for (User u : del) {
            mentionsUserList.remove(u);
        }

        return userMapper.entitiesToDtos(mentionsUserList);
    }


    //////////////////////////////////////////////////////////////////////////////////


    @Override
    public List<TweetResponseDto> getTweets() {

        List<Tweet> tweetList = tweetRepository.findAllByDeletedFalse();

        tweetList.sort(new SortByPostedReverse());
        return tweetMapper.entitiesToDtos(tweetList);

    }


    @Override
    public TweetResponseDto deleteTweet(Long id, CredentialsDto credentialsDto) {

        Optional<Tweet> tToDel = tweetRepository.findByIdAndDeletedFalse(id);


        if (tToDel.isEmpty() || !credentialsDto.getUsername().equals(tToDel.get().getAuthor().getCredentials().getUsername())) {


            throw new NotFoundException("No tweet found with id: " + id);


        }

        tToDel.get().setDeleted(true);


        return tweetMapper.entityToDto(tweetRepository.saveAndFlush(tToDel.get()));


    }

    @Override
    public List<UserResponseDto> getLikes(Long id) {


        Optional<Tweet> optionalTweet = tweetRepository.findByIdAndDeletedFalse(id);

        if (optionalTweet.isEmpty()) {


            throw new NotFoundException("No tweet found with id: " + id);

        }


        return tweetMapper.entitiesToUserDtos(optionalTweet.get().getLikesUserList());


    }

    @Override
    public TweetResponseDto getTweet(Long id) {


        Optional<Tweet> optionalTweet = tweetRepository.findByIdAndDeletedFalse(id);

        if (optionalTweet.isEmpty()) {


            throw new NotFoundException("No tweet found with id: " + id);

        }


        return tweetMapper.entityToDto(optionalTweet.get());

    }


    @Override
    public ContextDto getContext(Long id) {
        Optional<Tweet> optionalTweet = tweetRepository.findByIdAndDeletedFalse(id);
        if (optionalTweet.isEmpty()) {
            throw new NotFoundException("tweet not found");
        }
        ContextDto result = new ContextDto();
        result.setTarget(tweetMapper.entityToDto(optionalTweet.get()));
        List<Tweet> allTweets = tweetRepository.findAll();
        ArrayList<ArrayList<Tweet>> unsortedContexts = new ArrayList<>();
        for (Tweet _tweet : allTweets) {
            ArrayList<Tweet> uC = new ArrayList<>();
            while (_tweet != null) {
                uC.add(_tweet);
                _tweet = _tweet.getInReplyTo();
            }
            unsortedContexts.add(uC);
        }
        unsortedContexts.sort(new SortBySizeReverse());
        for (ArrayList<Tweet> l : unsortedContexts) {
            if (l.contains(optionalTweet.get())) {
                // found correct context
                // bucket sort
                ArrayList<Tweet> after = new ArrayList<>();
                ArrayList<Tweet> before = new ArrayList<>();
                for (Tweet _tweet : l) {
                    // guard deleted and target tweet
                    if (!_tweet.isDeleted() && !_tweet.equals(optionalTweet.get())) {
                        if (_tweet.getPosted().getTime() > optionalTweet.get().getPosted().getTime()) {
                            after.add(_tweet);
                        } else {
                            before.add(_tweet);
                        }
                    }
                }
                after.sort(new SortByPostedReverse()); // todo: verify this is not needed
                before.sort(new SortByPostedReverse()); // todo: verify this is not needed, context should be in posted date descending order already
                result.setAfter(tweetMapper.entitiesToDtos(after));
                result.setBefore(tweetMapper.entitiesToDtos(before));
                return result;
            }
        }
        result.setAfter(new ArrayList<>());
        result.setBefore(new ArrayList<>());
        return result;
    }


    @Override
    public List<TweetResponseDto> getReplies(Long id) {
        Optional<User> optionalUser = userRepository.findByIdAndDeletedFalse(id);
        List<Tweet> allTweets = tweetRepository.findAllByDeletedFalse();
        if (optionalUser.isEmpty()) {
            throw new BadRequestException("bad user id");
        }

        ArrayList<Tweet> result = new ArrayList<>();
        for (Tweet tweet : allTweets) {
            if (tweet.getInReplyTo() != null && tweet.getInReplyTo().getAuthor() == optionalUser.get()) {
                result.add(tweet);
            }
        }
        return tweetMapper.entitiesToDtos(result);
    }

    @Override
    public void likeTweet(Long id, CredentialsDto credentialsDto) {

        Optional<User> liker = userRepository.findByCredentialsUsernameAndDeletedFalse(credentialsDto.getUsername());

        if (liker.isEmpty() || !credentialsAreCorrect(liker, credentialsDto)) {

            throw new BadRequestException("Bad request");
        }


        Optional<Tweet> toBeLiked = tweetRepository.findByIdAndDeletedFalse(id);

        if (toBeLiked.isEmpty() || toBeLiked.get().isDeleted()) {


            throw new NotFoundException("No tweet found with id: " + id);

        }


        if (!toBeLiked.get().getLikesUserList().contains(liker.get())) {

            toBeLiked.get().getLikesUserList().add(liker.get());
            tweetRepository.saveAndFlush(toBeLiked.get());


        }


        if (!liker.get().getLikesTweetList().contains(toBeLiked.get())) {

            liker.get().getLikesTweetList().add(toBeLiked.get());

            userRepository.saveAndFlush(liker.get());


        }


    }


}