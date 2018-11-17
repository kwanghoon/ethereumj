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

import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
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
public class SimpleEscrowSample {
    private static final String escrowContractSrc =
            "pragma solidity ^0.4.8;\n" +
                    "\n" +
                    "contract MyCoin {\n" +
                    "    // 상태 변수 선언\n" +
                    "    string public name; // 토큰 이름\n" +
                    "    string public symbol; // 토큰 단위\n" +
                    "    uint8 public decimals; // 소수점 이하 자릿수\n" +
                    "    uint256 public totalSupply; // 토큰 총량\n" +
                    "    mapping (address => uint256) public balanceOf; // 각 주소의 잔고\n" +
                    "     \n" +
                    "    // 이벤트 알림\n" +
                    "    event Transfer(address indexed from, address indexed to, uint256 value);\n" +
                    "\n" +
                    "    // 생성자\n" +
                    "    function MyCoin(uint256 _supply, string _name, string _symbol, uint8 _decimals) {\n" +
                    "        balanceOf[msg.sender] = _supply;\n" +
                    "        name = _name;\n" +
                    "        symbol = _symbol;\n" +
                    "        decimals = _decimals;\n" +
                    "        totalSupply = _supply;\n" +
                    "    }\n" +
                    " \n" +
                    "// 송금\n" +
                    "    function transfer(address _to, uint256 _value) {\n" +
                    "        // 부정 송금 확인\n" +
                    "        if (balanceOf[msg.sender] < _value) throw;\n" +
                    "        if (balanceOf[_to] + _value < balanceOf[_to]) throw;\n" +
                    "\n" +
                    "        balanceOf[msg.sender] -= _value;\n" +
                    "        balanceOf[_to] += _value;\n" +
                    "        Transfer(msg.sender, _to, _value);\n" +
                    "    }\n" +
                    "}\n" +
                    "\n" +
                    "// (1) 에스크로\n" +
                    "contract Escrow {\n" +
                    "    // (2) 상태 변수\n" +
                    "    MyCoin public token; // 토큰\n" +
                    "    uint256 public salesVolume; // 판매량\n" +
                    "    uint256 public sellingPrice; // 판매 가격\n" +
                    "    uint256 public deadline; // 기한\n" +
                    "    bool public isOpened; // 에스크로 개시 플래그\n" +
                    "    address public owner; // 소유자 주소\n" +
                    "     \n" +
                    "    // (3) 이벤트 알림\n" +
                    "    event EscrowStart(uint salesVolume, uint sellingPrice, uint deadline, address beneficiary);\n" +
                    "    event ConfirmedPayment(address addr, uint amount);\n" +
                    "     \n" +
                    "    // 소유자 한정 메서드용 수식자\n" +
                    "    modifier onlyOwner() { if (msg.sender != owner) throw; _; }\n" +
                    "\n" +
                    "    // (4) 생성자\n" +
                    "    function Escrow (MyCoin _token, uint256 _salesVolume, uint256 _priceInEther) {\n" +
                    "        token = MyCoin(_token);\n" +
                    "        salesVolume = _salesVolume;\n" +
                    "        sellingPrice = _priceInEther * 1 ether;\n" +
                    "\n" +
                    "\towner = msg.sender; // 처음에 계약을 생성한 주소를 소유자로 한다\n" +
                    "    }\n" +
                    "     \n" +
                    "    // (5) 이름 없는 함수(Ether 수령)\n" +
                    "    function () payable {\n" +
                    "        // 개시 전 또는 기한이 끝난 경우에는 예외 처리\n" +
                    "        if (!isOpened || now >= deadline) throw;\n" +
                    "         \n" +
                    "        // 판매 가격 미만인 경우 예외 처리\n" +
                    "        uint amount = msg.value;\n" +
                    "        if (amount < sellingPrice) throw;\n" +
                    "         \n" +
                    "        // 보내는 사람에게 토큰을 전달하고 에스크로 개시 플래그를 false로 설정\n" +
                    "        token.transfer(msg.sender, salesVolume);\n" +
                    "        isOpened = false;\n" +
                    "        ConfirmedPayment(msg.sender, amount);\n" +
                    "    }\n" +
                    "     \n" +
                    "    // (6) 개시(토큰이 예정 수 이상이라면 개시)\n" +
                    "    function start(uint256 _durationInMinutes) onlyOwner {\n" +
                    "        if (token == address(0) || salesVolume == 0 || sellingPrice == 0 || deadline != 0) throw;\n" +
                    "        if (token.balanceOf(this) >= salesVolume){\n" +
                    "            deadline = now + _durationInMinutes * 1 minutes;\n" +
                    "            isOpened = true;\n" +
                    "            EscrowStart(salesVolume, sellingPrice, deadline, owner);  \n" +
                    "        }\n" +
                    "    }\n" +
                    "     \n" +
                    "    // (7) 남은 시간 확인용 메서드(분 단위)\n" +
                    "    function getRemainingTime() constant returns(uint min) {\n" +
                    "        if(now < deadline) {\n" +
                    "            min = (deadline - now) / (1 minutes);\n" +
                    "        }\n" +
                    "    }\n" +
                    "     \n" +
                    "    // (8) 종료\n" +
                    "    function close() onlyOwner {\n" +
                    "        // 토큰을 소유자에게 전송\n" +
                    "        token.transfer(owner, token.balanceOf(this));\n" +
                    "        // 계약을 파기(해당 계약이 보유하고 있는 Ether는 소유자에게 전송\n" +
                    "        selfdestruct(owner);\n" +
                    "    }\n" +
                    "}\n"
                ;

    StandaloneBlockchain bc;

    public static void main(String[] args) throws Exception {
        SimpleEscrowSample main = new SimpleEscrowSample();
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

        System.out.println("Creating accounts: seller, customer");

        BigInteger thousandWei = new BigInteger("1000");
        BigInteger eitherToWei = thousandWei.multiply(thousandWei).multiply(thousandWei)
                .multiply(thousandWei).multiply(thousandWei).multiply(thousandWei);

        //@@ account{balance:10ether} seller;
        Account seller = new Account("account seller");
        bc.sendEther(seller.getEckey().getAddress(), new BigInteger("1").multiply(eitherToWei));

        //@@ account{balance:50ether} customer;
        Account customer = new Account("account customer");
        bc.sendEther(customer.getEckey().getAddress(), new BigInteger("50").multiply(eitherToWei));

        BigInteger seller_bi = bc.getBlockchain().getRepository().getBalance(seller.getEckey().getAddress());
        BigInteger customer_bi = bc.getBlockchain().getRepository().getBalance(customer.getEckey().getAddress());

        System.out.println("seller   balance: " + seller_bi);
        System.out.println("customer balance: " + customer_bi);

        System.out.println("Creating a contract: MyCoin");
        // This compiles our Solidity contract, submits it to the blockchain
        // internally generates the block with this transaction and returns the
        // contract interface

        //@@ account{smart contract : coin.sol, by: seller, balance: 0eth} coin;
        bc.setSender(seller.getEckey());
        SolidityContract  coinContract = bc.submitNewContract(escrowContractSrc,
                "MyCoin",
                new Object[] { new BigInteger("5"), "mycoin", "MYC", new BigInteger("1") });

        System.out.println("Creating a contract: Escrow");

        //@@ account{smart contract : escrow.sol, by: seller, balance: 0eth} escrow(coin, 1, 5ether);
        bc.setSender(seller.getEckey());
        SolidityContract  escrowContract = bc.submitNewContract(escrowContractSrc, "Escrow",
                new Object[] { coinContract.getAddress(), 1, new BigInteger("5")});

        //@@ sendTransaction(coin, transfer, escrow, 1, {from=seller, gas:3000000, value:0ether});
        bc.setSender(seller.getEckey());
        SolidityCallResult result_transfer = coinContract.callFunction("transfer",
                new Object[] {
                        escrowContract.getAddress(),
                        new BigInteger("1")});
        System.out.println("transfer: " + result_transfer);

        //@@ sendTransaction(escrow, start, 60min, {from=seller, gas:3000000, value:0ether});
        bc.setSender(seller.getEckey());
        SolidityCallResult result_start = escrowContract.callFunction("start", 1);
        System.out.println("start: " + result_start);

        //@@ sendTransaction(escrow, fallack, {from=customer, gas:3000000, value:5ether});
        bc.setSender(customer.getEckey());
        SolidityCallResult result_fallback = escrowContract.callFunction(
                new BigInteger("5").multiply(eitherToWei).longValue(),
                "");
        System.out.println(result_fallback);

        //@@ sendTransaction(escrow, close, {from=intermediary, gas:3000000, value:0ether});
        bc.setSender(seller.getEckey());
        SolidityCallResult result_close = escrowContract.callFunction("close");
        System.out.println(result_close);

        seller_bi = bc.getBlockchain().getRepository().getBalance(seller.getEckey().getAddress());
        customer_bi = bc.getBlockchain().getRepository().getBalance(customer.getEckey().getAddress());

        System.out.println("seller   balance: " + seller_bi);
        System.out.println("customer balance: " + customer_bi);

        //@@ assert seller.balance >= 10ether + 5ether - delta;
        //@@ assert coin.balanceOf(customer) == 1;
        //@@ assert customer.balance >= 45ether - delta;

        BigInteger seller_balance = bc.getBlockchain().getRepository().getBalance(seller.getEckey().getAddress());
        BigInteger coin_balance = bc.getBlockchain().getRepository().getBalance(coinContract.getAddress());
        BigInteger customer_balance = bc.getBlockchain().getRepository().getBalance(customer.getEckey().getAddress());

        assert seller_balance.compareTo(new BigInteger("13")) == -1;// 10 + 5 - 2;
        assert coin_balance.compareTo(new BigInteger("1")) == 0;
        assert customer_balance.compareTo(new BigInteger("43")) == -1; // 45 - 2

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



