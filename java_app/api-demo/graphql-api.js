const { graphqlHTTP } = require("express-graphql");
const { buildSchema } = require("graphql");
const express = require("express");

const app = express();

let users = [
  { id: 1, name: "Huy", email: "huy@example.com" },
  { id: 2, name: "Minh", email: "minh@example.com" }
];

// Định nghĩa Schema
const schema = buildSchema(`
  type User {
    id: ID!
    name: String!
    email: String!
  }
  
  type Query {
    users: [User]
    user(id: ID!): User
  }

  type Mutation {
    addUser(name: String!, email: String!): User
    updateUser(id: ID!, name: String!, email: String!): User
  }
`);

// Định nghĩa Resolver
const root = {
  users: () => users,
  user: ({ id }) => users.find(u => u.id == id),
  addUser: ({ name, email }) => {
    const newUser = { id: users.length + 1, name, email };
    users.push(newUser);
    return newUser;
  },
  updateUser: ({id, name, email}) => {
    const index = users.findIndex(user => user.id == id);
    if (index !== -1) {
      users[index] = { ...users[index], ...{name, email} };
    }
    return users[index]
  }
  
};

// Kết nối Apollo Server với Express
app.use("/graphql", graphqlHTTP({ schema, rootValue: root, graphiql: true }));

// Chạy server
app.listen(4000, () => console.log("GraphQL API running on port 4000"));
