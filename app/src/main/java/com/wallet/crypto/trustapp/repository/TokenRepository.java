package com.wallet.crypto.trustapp.repository;

import android.util.Log;

import com.wallet.crypto.trustapp.entity.NetworkInfo;
import com.wallet.crypto.trustapp.entity.Token;
import com.wallet.crypto.trustapp.entity.TokenInfo;
import com.wallet.crypto.trustapp.entity.Wallet;
import com.wallet.crypto.trustapp.service.TokenExplorerClientType;
import com.wallet.crypto.trustapp.util.BallanceUtils;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jFactory;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.http.HttpService;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.SingleOperator;
import io.reactivex.functions.Function;
import io.reactivex.observers.DisposableSingleObserver;
import okhttp3.OkHttpClient;

public class TokenRepository implements TokenRepositoryType {

    private final TokenExplorerClientType tokenNetworkService;
    private final TokenLocalSource tokenLocalSource;
    private final OkHttpClient httpClient;
    private Web3j web3j;

    public TokenRepository(
            OkHttpClient okHttpClient,
            EthereumNetworkRepositoryType ethereumNetworkRepository,
            TokenExplorerClientType tokenNetworkService,
            TokenLocalSource tokenLocalSource) {
        this.httpClient = okHttpClient;
        this.tokenNetworkService = tokenNetworkService;
        this.tokenLocalSource = tokenLocalSource;
        ethereumNetworkRepository.addOnChangeDefaultNetwork(this::buildWeb3jClient);
        buildWeb3jClient(ethereumNetworkRepository.getDefaultNetwork());
    }

    private void buildWeb3jClient(NetworkInfo defaultNetwork) {
        web3j = Web3jFactory.build(new HttpService(defaultNetwork.rpcServerUrl, httpClient, false));
    }

    @Override
    public Observable<Token[]> fetch(String walletAddress) {
        return Observable.create(e -> {
            Wallet wallet = new Wallet(walletAddress);
            Token[] tokens = tokenLocalSource.fetch(wallet)
                    .map(items -> {
                        int len = items.length;
                        Token[] result = new Token[len];
                        for (int i = 0; i < len; i++) {
                            result[i] = new Token(items[i], null);
                        }
                        return result;
                    })
                    .blockingGet();
            e.onNext(tokens);

            tokenNetworkService
                    .fetch(walletAddress)
                    .flatMapCompletable(items -> Completable.fromAction(() -> {
                        for (TokenInfo tokenInfo : items) {
                            try {
                                tokenLocalSource.put(wallet, tokenInfo)
                                        .blockingAwait();
                            } catch (Throwable t) {
                                Log.d("TOKEN_REM", "Err", t);
                            }
                        }
                    })).blockingAwait();

            tokens = tokenLocalSource.fetch(wallet)
                    .map(new Function<TokenInfo[], Token[]>() {
                        @Override
                        public Token[] apply(TokenInfo[] items) throws Exception {
                            int len = items.length;
                            Token[] result = new Token[len];
                            for (int i = 0; i < len; i++) {
                                BigDecimal balance = null;
                                try {
                                    balance = getBalance(wallet, items[i]);
                                } catch (Exception e) {
                                    Log.d("TOKEN", "Err", e);
                                    /* Quietly */
                                }
                                result[i] = new Token(items[i], balance);
                            }
                            return result;
                        }
                    }).blockingGet();
            e.onNext(tokens);
        });
    }

    private BigDecimal getBalance(Wallet wallet, TokenInfo tokenInfo) throws Exception {
        org.web3j.abi.datatypes.Function function = balanceOf(wallet.address);
        String responseValue = callSmartContractFunction(function, tokenInfo.address, wallet);

        List<Type> response = FunctionReturnDecoder.decode(
                responseValue, function.getOutputParameters());
        if (response.size() == 1) {
            BigDecimal balance = new BigDecimal(((Uint256) response.get(0)).getValue());
            BigDecimal decimalDivisor = new BigDecimal(Math.pow(10, tokenInfo.decimals));
            return tokenInfo.decimals > 0 ? balance.divide(decimalDivisor) : balance;
        } else {
            return null;
        }
    }

    private static org.web3j.abi.datatypes.Function balanceOf(String owner) {
        return new org.web3j.abi.datatypes.Function(
                "balanceOf",
                Collections.singletonList(new Address(owner)),
                Collections.singletonList(new TypeReference<Uint256>() {}));
    }

    private String callSmartContractFunction(
            org.web3j.abi.datatypes.Function function, String contractAddress, Wallet wallet) throws Exception {
        String encodedFunction = FunctionEncoder.encode(function);

        org.web3j.protocol.core.methods.response.EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(wallet.address, contractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST)
                .sendAsync().get();

        return response.getValue();
    }
}
