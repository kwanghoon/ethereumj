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
public class SimpleKotET {
    private static final String kotETContractSrc =
        "pragma solidity ^0.4.0;\n" +
                "\n" +
                "contract KingOfTheEtherThrone {\n" +
                "    address public king;\n" +
                "    uint public claimPrice;// = 10 ether;\n" +
                "    address owner;\n" +
                "    uint public balance;\n" +
                "    //constructor, assigning ownership\n" +
                "    constructor() payable {\n" +
                "        owner = msg.sender;\n" +
                "        king = msg.sender;\n" +
                "        claimPrice = msg.value;\n" +
                "    }\n" +
                "    //for contract creator to withdraw commission fees\n" +
                "    function sweepCommission(uint amount) {\n" +
                "        owner.send(amount);\n" +
                "    }\n" +
                "    //fallback function\n" +
                "    function() payable {\n" +
                "        if (msg.value < claimPrice) revert();\n" +
                "        uint compensation = calculateCompensation();\n" +
                "\n" +
                "        king.send(compensation);\n" +
                "        king = msg.sender;\n" +
                "        claimPrice = msg.value; //calculateNewPrice();\n" +
                "    }\n" +
                "    function calculateCompensation() returns (uint) {\n" +
                "        return (claimPrice - 1 ether);\n" +
                "    }\n" +
                "    function getBal() public {\n" +
                "        balance = address(this).balance;\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "contract Victim {\n" +
                "\tKingOfTheEtherThrone public kotET;\n" +
                "\tuint public balance;\n" +
                "\tfunction Victim(KingOfTheEtherThrone _kotET) payable {\n" +
                "\t\tkotET = _kotET;\n" +
                "// \t\tbalance = address(this).balance;\n" +
                "\t}\n" +
                "\tfunction() {\n" +
                "\t    throw;\n" +
                "\t}\n" +
                "\tfunction claimThrone() public {\n" +
                "\t\tkotET.send(address(this).balance);\n" +
                "// \t\tkotET.call.value(address(this).balance);\n" +
                "\t}\n" +
                "\tfunction getBal() public {\n" +
                "        balance = address(this).balance;\n" +
                "    }\n" +
                "}"
                ;

    StandaloneBlockchain bc;

    public static void main(String[] args) throws Exception {
        SimpleKotET main = new SimpleKotET();
        main.simpleEscrowContract(args);
    }

    public void simpleEscrowContract(String[] args) throws Exception {
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

        System.out.println("Creating accounts: owner, user, and hacker");

        BigInteger thousandWei = new BigInteger("1000");
        BigInteger eitherToWei = thousandWei.multiply(thousandWei).multiply(thousandWei)
                .multiply(thousandWei).multiply(thousandWei).multiply(thousandWei);

        //@@ account{balance:50ether} owner;
        Account owner = new Account("account owner");
        bc.sendEther(owner.getEckey().getAddress(), new BigInteger("50").multiply(eitherToWei));

        //@@ account{balance:50ether} user;
        Account user = new Account("account user");
        bc.sendEther(user.getEckey().getAddress(), new BigInteger("50").multiply(eitherToWei));

        //@@ account{balance:50ether} hacker;
        Account hacker = new Account("account hacker");
        bc.sendEther(hacker.getEckey().getAddress(), new BigInteger("50").multiply(eitherToWei));

        System.out.println("Creating a contract: KingOfTheEtherThrone");
        bc.setSender(owner.getEckey());
        SolidityContract  kotETContract = bc.submitNewContract(kotETContractSrc,
                "KingOfTheEtherThrone",
                new BigInteger("10").multiply(eitherToWei));

        System.out.println("Creating a contract: Victim");
        bc.setSender(owner.getEckey());
        SolidityContract  victimContract = bc.submitNewContract(kotETContractSrc,
                "Victim",
                new BigInteger("12").multiply(eitherToWei),
                new Object[] { ByteUtil.bytesToBigInteger(kotETContract.getAddress()) });


        System.out.println("kotet.(){by:user,value:11ether};");
        bc.setSender(user.getEckey());
        SolidityCallResult result_fallback = kotETContract.callFunction(
                new BigInteger("11").multiply(eitherToWei).longValue(),
                "");
        System.out.println(result_fallback);

        System.out.println("victim.claimThrone(){by:hacker};");
        bc.setSender(hacker.getEckey());
        SolidityCallResult result_claimThrome = victimContract.callFunction("claimThrone");
        System.out.println(result_claimThrome);

        BigInteger owner_bi = bc.getBlockchain().getRepository().getBalance(owner.getEckey().getAddress());
        BigInteger user_bi = bc.getBlockchain().getRepository().getBalance(user.getEckey().getAddress());
        BigInteger hacker_bi = bc.getBlockchain().getRepository().getBalance(hacker.getEckey().getAddress());

        System.out.println("owner balance: " + owner_bi);
        System.out.println("user balance: " + user_bi);
        System.out.println("hacker balance: " + hacker_bi);

        BigInteger kotet_bi = bc.getBlockchain().getRepository().getBalance(kotETContract.getAddress());
        System.out.println("kotET contract balance: " + kotet_bi);

        BigInteger victim_bi = bc.getBlockchain().getRepository().getBalance(victimContract.getAddress());
        System.out.println("Victim contract balance: " + victim_bi);

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



