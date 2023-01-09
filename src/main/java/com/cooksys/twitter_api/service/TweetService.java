package com.cooksys.twitter_api.service;

import com.cooksys.twitter_api.dtos.*;
import org.springframework.http.ResponseEntity;

import java.util.List;


public interface TweetService {

	  List<TweetResponseDto> getTags(Long id, String Label);
	  
	  List<TweetResponseDto> getReposts(Long id);
	  
	  
	  List<UserResponseDto> getMentions(Long id);
	  

	  TweetResponseDto replyToTweet(Long id, TweetRequestDto tweetRequestDto);


	  TweetResponseDto createTweet(TweetRequestDto tweetRequestDto);

	  TweetResponseDto repostTweet(Long ID, CredentialsDto credentialsDto);
	  
	  void likeTweet(Long id, CredentialsDto credentialsDto);
	  
	  List<TweetResponseDto> getTweets();
		  
	  TweetResponseDto deleteTweet(Long id, CredentialsDto credentialsDto);	

	  List<UserResponseDto> getLikes(Long id);

	  TweetResponseDto getTweet(Long id);
	  
	  ContextDto getContext(Long id);
	  
	  List<TweetResponseDto> getReplies(Long id);

}
