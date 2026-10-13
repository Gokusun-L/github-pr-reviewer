package demo;

public class Calculator {

  private static final String API_KEY = "sk-demo-1234567890abcdef";

  public int add(int a, int b) {
    return a + b;
  }

  public int divide(int a, int b) {
    // TODO: 处理除零
    return a / b;
  }
}