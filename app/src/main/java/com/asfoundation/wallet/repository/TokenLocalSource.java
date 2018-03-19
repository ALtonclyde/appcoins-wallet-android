package com.asfoundation.wallet.repository;

import com.asfoundation.wallet.entity.NetworkInfo;
import com.asfoundation.wallet.entity.Token;
import com.asfoundation.wallet.entity.TokenTicker;
import com.asfoundation.wallet.entity.Wallet;
import io.reactivex.Completable;
import io.reactivex.Single;

public interface TokenLocalSource {
  Completable saveTokens(NetworkInfo networkInfo, Wallet wallet, Token[] items);

  void updateTokenBalance(NetworkInfo network, Wallet wallet, Token token);

  void setEnable(NetworkInfo network, Wallet wallet, Token token, boolean isEnabled);

  Single<Token[]> fetchEnabledTokens(NetworkInfo networkInfo, Wallet wallet);

  Single<Token[]> fetchAllTokens(NetworkInfo networkInfo, Wallet wallet);

  Completable saveTickers(NetworkInfo network, Wallet wallet, TokenTicker[] tokenTickers);

  Single<TokenTicker[]> fetchTickers(NetworkInfo network, Wallet wallet, Token[] tokens);

  Completable delete(NetworkInfo network, Wallet wallet, Token token);
}
