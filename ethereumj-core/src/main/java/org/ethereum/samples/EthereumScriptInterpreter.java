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
import org.ethereum.util.blockchain.SolidityCallResult;
import org.ethereum.util.blockchain.SolidityContract;
import org.ethereum.util.blockchain.StandaloneBlockchain;
import org.swlab.lib.parser.examples.etherscript.Parser;
import org.swlab.lib.parser.examples.etherscript.ast.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * The class demonstrates usage of the StandaloneBlockchain helper class
 * which greatly simplifies Solidity contract testing on a locally created
 * blockchain
 *
 * Created by Anton Nashatyrev on 04.04.2016.
 */
public class EthereumScriptInterpreter {
    StandaloneBlockchain bc;
    static final BigInteger thousandWei = new BigInteger("1000");
    static final BigInteger eitherToWei =
            thousandWei.multiply(thousandWei).multiply(thousandWei)
                .multiply(thousandWei).multiply(thousandWei).multiply(thousandWei);
    static final BigInteger eitherToFinney =
            thousandWei.multiply(thousandWei).multiply(thousandWei)
                    .multiply(thousandWei).multiply(thousandWei);

    String scriptbase;

    public static void main(String[] args) throws Exception {
        EthereumScriptInterpreter main = new EthereumScriptInterpreter();
        main.simpleStorageSmartContract(args);
    }

    public void simpleStorageSmartContract(String[] args) throws Exception {
        String base = System.getProperty("user.dir");
        System.out.println("base dir: " + base);

        scriptbase =
                base + "/src/main/java/org/swlab/lib/parser/examples/etherscript/test/testcase/";

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

        String[] benchmarks = { "simplestorage.es", "dao.es", "escrow.es" };

        String scriptFileName = benchmarks[1];
        System.out.println("Script: " + scriptbase + scriptFileName);
        System.out.println(readFile(scriptbase + scriptFileName));

        FileReader fr = new FileReader(scriptbase + scriptFileName);
        Parser parser = new Parser();

        parser.setWorkingDir(base + "/out/production/");

        ArrayList<Stmt> program = (ArrayList<Stmt>)parser.Parsing(fr);

        HashMap<String,Object> env = new HashMap<String,Object>();
        HashMap<String,Type> tyenv = new HashMap<String,Type>();

        evalProgram(env, tyenv, program);

        System.out.println("Done.");
    }

    // Interpreter main routines
    void evalProgram(HashMap<String,Object> env, HashMap<String, Type>tyenv, ArrayList<Stmt> prog) {
        for(int i=0; i<prog.size(); i++) {
            System.out.println((i+1) + " : " + prog.get(i));
            evalStmt(env, tyenv, prog.get(i));
        }
    }

    void evalStmt(HashMap<String,Object> env, HashMap<String, Type>tyenv, Stmt stmt) {
        if (stmt instanceof org.swlab.lib.parser.examples.etherscript.ast.Account) {
            evalAccountStmt(env, tyenv, (org.swlab.lib.parser.examples.etherscript.ast.Account)stmt);
        } else if (stmt instanceof org.swlab.lib.parser.examples.etherscript.ast.SendTransaction) {
            evalSendTransactionStmt(env, tyenv, (org.swlab.lib.parser.examples.etherscript.ast.SendTransaction)stmt);
        } else if (stmt instanceof Assert) {
            evalAssertStmt(env, tyenv, (Assert)stmt);
        } else if (stmt instanceof VarDecl) {
            evalVarDeclStmt(env, tyenv, (VarDecl)stmt);
        } else
            assert false;
    }

    void evalAccountStmt(HashMap<String,Object> env, HashMap<String, Type>tyenv,
                         org.swlab.lib.parser.examples.etherscript.ast.Account accStmt) {
        Expr contractExpr = accStmt.properties.get("contract");
        Expr balanceExpr = accStmt.properties.get("balance");
        Expr byExpr = accStmt.properties.get("by");
        ArrayList<Expr> argExprs = accStmt.argExprs;

        // Account declaration
        if (contractExpr == null && balanceExpr != null && argExprs == null) {
            Account accId = new Account("account " + accStmt.name);
            BigInteger bal = (BigInteger)evalExpr(env, tyenv, balanceExpr);
            bc.sendEther(accId.getEckey().getAddress(), bal);

            env.put(accStmt.name, accId);

            Type addressType = new Type();
            addressType.kind = Type.ADDRESS;
            tyenv.put(accStmt.name, addressType);
        }
        // Contract account declaration
        else if (contractExpr != null && byExpr != null && argExprs.size() >= 1) {
            Account byAcc = (Account)evalExpr(env, tyenv, byExpr);
            String solidityFileName = (String)evalExpr(env, tyenv, contractExpr);
            String soliditySrc = readFile(scriptbase + solidityFileName);

            String constrName = (String)evalExpr(env, tyenv, argExprs.get(0));
            argExprs.remove(0);

            boolean argsAvailable = true;

            if (argExprs == null || argExprs.size() == 0) {
                argsAvailable = false;
            }

            bc.setSender(byAcc.getEckey());

            SolidityContract contractId;
            if (argsAvailable == false) {
                contractId = bc.submitNewContract(soliditySrc, constrName);
            } else {
                Object[] argVals = evalExprs(env, tyenv, argExprs);
                Object[] convArgVals = toAddress(argVals);
                contractId = bc.submitNewContract(soliditySrc, constrName, convArgVals);
            }

            env.put(accStmt.name, contractId);

            Type contractType = new Type();
            contractType.kind = Type.CONTRACT;
            tyenv.put(accStmt.name, contractType);
        }
        //Not supported
        else {
            System.err.println(accStmt);
            assert false;
        }
    }

    void evalSendTransactionStmt(HashMap<String,Object> env, HashMap<String, Type>tyenv,
                         org.swlab.lib.parser.examples.etherscript.ast.SendTransaction sendTranStmt) {
        Expr byExpr = sendTranStmt.properties.get("by");
        Expr balanceExpr = sendTranStmt.properties.get("value");
        ArrayList<Expr> argExprs = sendTranStmt.argExprs;
        Object[] argVals = evalExprs(env, tyenv, argExprs);
        Object[] convArgVals;

        if(byExpr != null) {
            Account senderAcc = (Account)evalExpr(env, tyenv, byExpr);
            bc.setSender(senderAcc.getEckey());

            SolidityContract contractId = (SolidityContract)env.get(sendTranStmt.contractName);
            SolidityCallResult result;

            if(balanceExpr == null) {
                if (argVals == null) {
                    System.out.println("sendTransactionStmt:(1)");
                    result = contractId.callFunction(sendTranStmt.functionName);
                } else {
                    System.out.println("sendTransactionStmt:(2)");
                    convArgVals = toAddress(argVals);
                    result = contractId.callFunction(sendTranStmt.functionName, convArgVals);
                }
            } else {
                BigInteger bal = (BigInteger)evalExpr(env, tyenv, balanceExpr);
                if (argVals == null) {
                    System.out.println("sendTransactionStmt:(3)");
                    result = contractId.callFunction(bal.longValue(), sendTranStmt.functionName);
                } else {
                    System.out.println("sendTransactionStmt:(4)");
                    convArgVals = toAddress(argVals);
                    result = contractId.callFunction(bal.longValue(), sendTranStmt.functionName, convArgVals);
                }
            }
            System.out.println(result);

            if (sendTranStmt.retVar != null) {
                env.put(sendTranStmt.retVar, result.getReturnValues()[0]);
            }
        } else {
            assert false;
        }
    }

    void evalAssertStmt(HashMap<String,Object> env, HashMap<String, Type>tyenv, Assert assertStmt) {
        Expr conditional = assertStmt.conditional;
        Object val = evalExpr(env, tyenv, conditional);

        if (val instanceof Boolean) {
            Boolean decision = (Boolean) val;
            System.out.println("assert: " + decision);
        } else if (val instanceof BigInteger) {
            BigInteger bi = (BigInteger) val;
            System.out.println("assert: " + bi);
        } else {
            System.out.println("assert: " + val);
        }
    }

    void evalVarDeclStmt(HashMap<String,Object> env, HashMap<String, Type>tyenv, VarDecl varDeclStmt) {
        String var = varDeclStmt.name;
        Type type = varDeclStmt.type;

        tyenv.put(var, type);
    }

    Object[] evalExprs(HashMap<String,Object> env, HashMap<String, Type>tyenv, ArrayList<Expr> exprs) {
        if (exprs != null) {
            Object[] argVals = new Object[exprs.size()];

            for (int i = 0; i < exprs.size(); i++) {
                argVals[i] = evalExpr(env, tyenv, exprs.get(i));
            }
            return argVals;
        }
        else {
            return null;
        }
    }

    Object evalExpr(HashMap<String,Object> env, HashMap<String, Type>tyenv, Expr expr) {
        if (expr instanceof Literal) {
            return evalLiteralExpr(env, tyenv, (Literal)expr);
        } else if (expr instanceof PrimOp) {
            return evalPrimOpExpr(env, tyenv, (PrimOp)expr);
        } else if (expr instanceof FieldAccess) {
            return evalFieldAccessExpr(env, tyenv, (FieldAccess)expr);
        } else if (expr instanceof Var) {
            return evalVarExpr(env, tyenv, (Var)expr);
        }
        assert false;
        return null;
    }

    Object evalLiteralExpr(HashMap<String,Object> env, HashMap<String, Type>tyenv, Literal lit) {
        switch(lit.kind) {
            case Literal.DECIMAL_NUMBER:
                return evalLiteralNumberExpr(env, tyenv, lit, 10);
            case Literal.HEX_NUMBER:
                return evalLiteralNumberExpr(env, tyenv, lit, 16);
            case Literal.BOOLEAN:
                if ("true".equals(lit.literal)) return true;
                else if ("false".equals(lit.literal)) return false;
                else assert false;
                break;
            case Literal.STRING:
                return lit.literal.substring(1,lit.literal.length()-1);
            default:
                assert false;
        }
        return null;
    }

    Object evalLiteralNumberExpr(HashMap<String,Object> env, HashMap<String, Type>tyenv, Literal lit, int weight) {
        BigInteger bi = new BigInteger(lit.literal, weight);
        if (lit.unit != null) {
            switch (lit.unit) {
                case "ether":
                    return bi.multiply(eitherToWei);
                case "wei":
                    return bi;
                case "finney":
                    return bi.multiply(eitherToFinney);
                case "seconds":
                    return bi;
                case "minutes":
                    return bi.multiply(new BigInteger("60"));
                case "hours":
                    return bi.multiply(new BigInteger("60")).multiply(new BigInteger("60"));
                default:
                    assert false;
                    return null;
            }
        }
        else
            return bi;
    }

    Object evalPrimOpExpr(HashMap<String,Object> env, HashMap<String, Type>tyenv, PrimOp primop) {
        switch(primop.kind) {
        case PrimOp.EQ:
            Object op1 = evalExpr(env, tyenv, primop.op1);
            Object op2 = evalExpr(env, tyenv, primop.op2);
            return op1.equals(op2);  // for BigInteger, String, Boolean
        }
        return null;
    }

    Object evalFieldAccessExpr(HashMap<String,Object> env, HashMap<String, Type>tyenv, FieldAccess fieldexpr) {
        Object objVal = evalExpr(env, tyenv, fieldexpr.obj);
        if (objVal instanceof Account ) {
            assert fieldexpr.field.equals("balance");
            Account account = (Account)objVal;
            return bc.getBlockchain().getRepository().getBalance(account.getEckey().getAddress());
        } else if (objVal instanceof SolidityContract) {
            SolidityContract contract = (SolidityContract)objVal;
            return contract.callConstFunction(fieldexpr.field)[0];
        }
        else {
            assert false;
            return null;
        }
    }

    Object evalVarExpr(HashMap<String,Object> env, HashMap<String, Type>tyenv, Var var) {
        return env.get(var.name);
    }

    // Libraries for the Ethereum Script Interpreter
    private static void assertEqual(BigInteger n1, BigInteger n2) {
        if (!n1.equals(n2)) {
            throw new RuntimeException("Assertion failed: " + n1 + " != " + n2);
        }
    }

    private static String readFile(String filename) {
        StringBuilder sb = new StringBuilder();
        try {
            FileReader fr = new FileReader(filename);
            int ch;
            while ((ch = fr.read()) != -1) {
                sb.append((char)ch);
            }
            fr.close();
        } catch(FileNotFoundException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
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

    Object[] toAddress(Object[] argVals) {
        Object[] convertedArgVals = new Object[argVals.length];
        for(int i= 0; i<argVals.length; i++) {
            convertedArgVals[i] = argVals[i];
            if (argVals[i] instanceof Account) {
                convertedArgVals[i] = ((Account)convertedArgVals[i]).getEckey().getAddress();
            }
            else if (argVals[i] instanceof SolidityContract) {
                convertedArgVals[i] = ((SolidityContract)convertedArgVals[i]).getAddress();
            }
        }
        return convertedArgVals;
    }
}



